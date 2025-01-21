package no.nav.helse.flex.soknadsopprettelse.aaregdata

import no.nav.helse.flex.client.aareg.AaregClient
import no.nav.helse.flex.client.aareg.Arbeidsforhold
import no.nav.helse.flex.client.ereg.EregClient
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.parseEgenmeldingsdagerFraSykmelding
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.arbeidGjenopptattDato
import no.nav.helse.flex.logger
import no.nav.helse.flex.soknadsopprettelse.prettyOrgnavn
import no.nav.helse.flex.util.erHelg
import no.nav.helse.flex.util.isBeforeOrEqual
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class AaregDataHenting(
    val aaregClient: AaregClient,
    val eregClient: EregClient,
    val environmentToggles: EnvironmentToggles,
) {
    val log = logger()

    fun hentNyeArbeidsforhold(
        fnr: String,
        sykepengesoknad: Sykepengesoknad,
        eksisterendeSoknader: List<Sykepengesoknad>,
    ): List<ArbeidsforholdFraAAreg> {
        val overlapperMedAndreArbeidsgivereEllerArbeidssituasjoner =
            eksisterendeSoknader
                .filter { it.fom != null && it.tom != null }
                .filter { it.arbeidsgiverOrgnummer != sykepengesoknad.arbeidsgiverOrgnummer }
                .any { it.tilPeriode().overlapper(sykepengesoknad.tilPeriode()) }

        if (overlapperMedAndreArbeidsgivereEllerArbeidssituasjoner) {
            log.info(
                "Fant overlapp med andre arbeidsgivere eller arbeidssituasjoner for søknad ${sykepengesoknad.id}. " +
                    "Ser ikke etter nye arbeidsforhold",
            )
            return emptyList()
        }

        val alleArbeidsforhold = aaregClient.hentArbeidsforhold(fnr).mergeKantIKant()

        if (environmentToggles.isQ()) {
            log.info("Hentet aaregdata for søknad ${sykepengesoknad.id} \n${alleArbeidsforhold.serialisertTilString()}")
        }

        fun Arbeidsforhold.tilArbeidsforholdFraAAreg(): ArbeidsforholdFraAAreg {
            val arbeidstedOrgnummer =
                this.arbeidssted.identer.firstOrNull { it.type == "ORGANISASJONSNUMMER" }?.ident
                    ?: throw RuntimeException(
                        "Fant ikke orgnummer for arbeidsforhold " +
                            "ved henting av nye arbeidsforhold for søknad ${sykepengesoknad.id}",
                    )

            val opplysningspliktigOrgnummer =
                this.opplysningspliktig.identer.firstOrNull { it.type == "ORGANISASJONSNUMMER" }?.ident
                    ?: throw RuntimeException(
                        "Fant ikke opplysningspliktig orgnummer for arbeidsforhold " +
                            "ved henting av nye arbeidsforhold for søknad ${sykepengesoknad.id}",
                    )

            val hentBedrift = eregClient.hentBedrift(arbeidstedOrgnummer)
            return ArbeidsforholdFraAAreg(
                arbeidsstedOrgnummer = arbeidstedOrgnummer,
                arbeidsstedNavn = hentBedrift.navn.sammensattnavn.prettyOrgnavn(),
                startdato = ansettelsesperiode.startdato,
                opplysningspliktigOrgnummer = opplysningspliktigOrgnummer,
                sluttdato = ansettelsesperiode.sluttdato,
            )
        }

        if (alleArbeidsforhold.harMerEnnEttAnnetAktivtArbeidsforhold(sykepengesoknad)) {
            log.info("Fant mer enn ett annet aktivt arbeidsforhold for søknad ${sykepengesoknad.id}. Ser ikke etter nye arbeidsforhold")
            return emptyList()
        }

        val arbeidsforholdSoknad =
            alleArbeidsforhold.find { arbeidsforholdOversikt ->
                arbeidsforholdOversikt.arbeidssted.identer.firstOrNull { it.ident == sykepengesoknad.arbeidsgiverOrgnummer } != null
            }
        val opplysningspliktigOrgnummer =
            arbeidsforholdSoknad?.opplysningspliktig?.identer?.firstOrNull { it.type == "ORGANISASJONSNUMMER" }?.ident

        return alleArbeidsforhold
            .filter { it.ansettelsesperiode.startdato.isAfter(sykepengesoknad.startSykeforlop) }
            .filter { it.ansettelsesperiode.startdato.isBeforeOrEqual(sykepengesoknad.tom!!) }
            .filter { it.erIkkeVidereforingAvAnnetArbeidsforhold(alleArbeidsforhold) }
            .filter { it.erOrganisasjonArbeidsforhold() }
            .filter {
                ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle(
                    arbeidsforhold = it,
                    eksisterendeSoknader = eksisterendeSoknader,
                    sykepengesoknad = sykepengesoknad,
                )
            }
            .filter { arbeidsforhold ->
                arbeidsforhold.arbeidssted.identer
                    .firstOrNull { it.type == "ORGANISASJONSNUMMER" }?.ident != sykepengesoknad.arbeidsgiverOrgnummer
            }
            .filterInterneOrgnummer(opplysningspliktigOrgnummer)
            .map { it.tilArbeidsforholdFraAAreg() }
            .sortedBy { it.startdato }
    }
}

private fun List<Arbeidsforhold>.harMerEnnEttAnnetAktivtArbeidsforhold(sykepengesoknad: Sykepengesoknad): Boolean {
    val andreAktiveArbeidsforhold =
        this
            .filter { a -> a.opplysningspliktig.identer.none { it.ident == sykepengesoknad.arbeidsgiverOrgnummer } }
            .filter { a -> a.arbeidssted.identer.none { it.ident == sykepengesoknad.arbeidsgiverOrgnummer } }
            .filter { it.ansettelsesperiode.sluttdato == null }
            .toSet()
    return andreAktiveArbeidsforhold
        .size > 1
}

private fun Sykepengesoknad.tilPeriode(): Periode {
    return Periode(fom!!, tom!!)
}

private fun Arbeidsforhold.erOrganisasjonArbeidsforhold(): Boolean {
    return this.opplysningspliktig.type == "Hovedenhet"
}

private fun List<Arbeidsforhold>.filterInterneOrgnummer(opplysningspliktigOrgnummer: String?): List<Arbeidsforhold> {
    opplysningspliktigOrgnummer ?: return this
    return this.filter { arbeidsforhold ->
        arbeidsforhold.opplysningspliktig.identer.none { it.ident == opplysningspliktigOrgnummer }
    }
}

data class ArbeidsforholdFraAAreg(
    val opplysningspliktigOrgnummer: String,
    val arbeidsstedOrgnummer: String,
    val arbeidsstedNavn: String,
    val startdato: LocalDate,
    val sluttdato: LocalDate?,
)

fun ingenArbeidsdagerMellomStartdatoOgEtterStartsyketilfelle(
    arbeidsforhold: Arbeidsforhold,
    eksisterendeSoknader: List<Sykepengesoknad>,
    sykepengesoknad: Sykepengesoknad,
): Boolean {
    val eksisterendeDenneAg =
        eksisterendeSoknader.filter { it.arbeidsgiverOrgnummer == sykepengesoknad.arbeidsgiverOrgnummer }

    val startdato = arbeidsforhold.ansettelsesperiode.startdato
    if (sykepengesoknad.fom!! < startdato) {
        return true
    }
    val alleDagerMellomStartdatoOgFom = startdato.datesUntil(sykepengesoknad.fom).toList().toSet()

    val alleHelgedagerMellomStartdatoOgEtterStartsyketilfelle =
        alleDagerMellomStartdatoOgFom.filter { it.erHelg() }.toSet()

    val sykedager =
        listOf(*eksisterendeDenneAg.toTypedArray(), sykepengesoknad)
            .asSequence()
            .filter { it.status != Soknadstatus.KORRIGERT }
            .map { Periode(fom = it.fom!!, tom = arbeidGjenopptattDato(it)?.minusDays(1) ?: it.tom!!) }
            .map { it.fom.datesUntil(it.tom.plusDays(1)).toList() }
            .flatten()
            .toSet()

    val egenmeldingsdager =
        listOf(*eksisterendeDenneAg.toTypedArray(), sykepengesoknad)
            .map { it.egenmeldingsdagerFraSykmelding.parseEgenmeldingsdagerFraSykmelding() }
            .flatMap { it ?: emptyList() }

    return alleDagerMellomStartdatoOgFom
        .minus(alleHelgedagerMellomStartdatoOgEtterStartsyketilfelle)
        .minus(sykedager)
        .minus(egenmeldingsdager)
        .isEmpty()
}
