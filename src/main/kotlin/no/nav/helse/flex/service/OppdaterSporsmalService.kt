package no.nav.helse.flex.service

import no.nav.helse.flex.client.kvitteringer.SykepengesoknadKvitteringerClient
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Arbeidssituasjon.*
import no.nav.helse.flex.domain.Soknadstype.*
import no.nav.helse.flex.domain.Soknadstype.ARBEIDSLEDIG
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.flatten
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SporsmalDAO
import no.nav.helse.flex.repository.SvarDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.oppdaterMedSvarPaFriskmeldingSporsmal
import no.nav.helse.flex.svarvalidering.tilKvittering
import no.nav.helse.flex.svarvalidering.validerSvarPaSporsmal
import no.nav.helse.flex.util.Metrikk
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class OppdaterSporsmalService(
    val sykepengesoknadKvitteringerClient: SykepengesoknadKvitteringerClient,
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val sporsmalDAO: SporsmalDAO,
    val svarDAO: SvarDAO,
    val metrikk: Metrikk
) {
    val log = logger()

    fun List<Sporsmal>.erUlikUtenomSvar(sammenlign: List<Sporsmal>): Boolean {
        fun List<Sporsmal>.flattenOgFjernSvar(): List<Sporsmal> {
            return this.flatten().map { it.copy(svar = emptyList(), undersporsmal = emptyList()) }.sortedBy { it.id }
        }

        return this.flattenOgFjernSvar() != sammenlign.flattenOgFjernSvar()
    }

    class OppdaterSporsmalResultat(val oppdatertSoknad: Sykepengesoknad, val mutert: Boolean)

    fun oppdaterSporsmal(soknadFraBasenForOppdatering: Sykepengesoknad, sporsmal: Sporsmal): OppdaterSporsmalResultat {
        val sporsmaletFraBasen = soknadFraBasenForOppdatering.sporsmal.find { it.id == sporsmal.id }
            ?: throw IllegalArgumentException("Soknad fra basen skal ha spørsmålet")

        if (listOf(sporsmal).erUlikUtenomSvar(listOf(sporsmaletFraBasen))) {
            throw IllegalArgumentException("Spørsmål i databasen er ulikt spørsmål som er besvart")
        }

        sporsmal.validerSvarPaSporsmal()

        val soknadMedOppdatertSporsmal = soknadFraBasenForOppdatering.replaceSporsmal(sporsmal)

        val oppdatertSoknad = oppdaterSoknad(soknadFraBasenForOppdatering, soknadMedOppdatertSporsmal, sporsmal)

        val soknadenErMutert = soknadFraBasenForOppdatering.sporsmal.erUlikUtenomSvar(oppdatertSoknad.sporsmal)

        if (soknadenErMutert) {
            sykepengesoknadDAO.byttUtSporsmal(oppdatertSoknad)
        } else {
            svarDAO.overskrivSvar(listOf(sporsmal).flatten())
        }
        return OppdaterSporsmalResultat(oppdatertSoknad, soknadenErMutert)
    }

    fun oppdaterSoknad(
        soknadFraBasen: Sykepengesoknad,
        soknadMedOppdateringer: Sykepengesoknad,
        sporsmal: Sporsmal
    ): Sykepengesoknad {
        return when (soknadMedOppdateringer.soknadstype) {
            ARBEIDSTAKERE -> {
                oppdaterSporsmalArbeidstakersoknad(
                    soknadFraBasen = soknadFraBasen,
                    soknadMedOppdateringer = soknadMedOppdateringer,
                    sporsmal = sporsmal,
                )
            }

            SELVSTENDIGE_OG_FRILANSERE -> {
                oppdaterSporsmalSelvstendigFrilansersoknad(
                    soknadFraBasen = soknadFraBasen,
                    soknadMedOppdateringer = soknadMedOppdateringer,
                    sporsmal = sporsmal,
                )
            }

            ARBEIDSLEDIG, ANNET_ARBEIDSFORHOLD -> {
                oppdaterSporsmalArbeidsledigOgAnnetsoknad(
                    soknadFraBasen = soknadFraBasen,
                    soknadMedOppdateringer = soknadMedOppdateringer,
                    sporsmal = sporsmal,
                )
            }

            GRADERT_REISETILSKUDD -> {
                when (soknadMedOppdateringer.arbeidssituasjon) {
                    ARBEIDSTAKER -> {
                        oppdaterSporsmalArbeidstakersoknad(
                            soknadFraBasen = soknadFraBasen,
                            soknadMedOppdateringer = soknadMedOppdateringer,
                            sporsmal = sporsmal,
                        )
                    }

                    FRILANSER, NAERINGSDRIVENDE -> {
                        oppdaterSporsmalSelvstendigFrilansersoknad(
                            soknadFraBasen = soknadFraBasen,
                            soknadMedOppdateringer = soknadMedOppdateringer,
                            sporsmal = sporsmal
                        )
                    }

                    Arbeidssituasjon.ARBEIDSLEDIG, ANNET -> {
                        oppdaterSporsmalArbeidsledigOgAnnetsoknad(
                            soknadFraBasen = soknadFraBasen,
                            soknadMedOppdateringer = soknadMedOppdateringer,
                            sporsmal = sporsmal
                        )
                    }

                    else -> throw IllegalStateException(
                        "Arbeidssituasjon ${soknadMedOppdateringer.arbeidssituasjon} skal ikke kunne ha gradert " +
                            "reisetilskudd"
                    )
                }
            }

            OPPHOLD_UTLAND -> {
                oppdaterSporsmalUtlandssoknad(soknadMedOppdateringer = soknadMedOppdateringer, sporsmal = sporsmal)
            }

            BEHANDLINGSDAGER -> {
                oppdaterSporsmalUtenLogikk(soknadMedOppdateringer = soknadMedOppdateringer, sporsmal = sporsmal)
            }

            REISETILSKUDD -> {
                oppdaterSporsmalUtenLogikk(soknadMedOppdateringer = soknadMedOppdateringer, sporsmal = sporsmal)
            }
        }
    }

    private fun oppdaterSporsmalArbeidstakersoknad(
        soknadFraBasen: Sykepengesoknad,
        soknadMedOppdateringer: Sykepengesoknad,
        sporsmal: Sporsmal,
    ): Sykepengesoknad {
        if (harEndretSvarPaArbeidGjenopptatt(soknadMedOppdateringer, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(soknadMedOppdateringer))
        } else if (harEndretSvarPaFeriePermisjonUtland(
                soknadMedOppdateringer,
                soknadFraBasen
            ) || harSoktOmSykepengerIUtlandetSomUndersporsmal(soknadMedOppdateringer)
        ) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaUtlandsopphold(soknadMedOppdateringer))
        } else if (harEndretSvarPaBrukteReisetilskuddet(soknadMedOppdateringer, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaBrukteReisetilskuddet(soknadMedOppdateringer))
        } else {
            svarDAO.overskrivSvar(listOf(sporsmal).flatten())
        }
        return sykepengesoknadDAO.finnSykepengesoknad(soknadMedOppdateringer.id)
    }

    private fun oppdaterSporsmalSelvstendigFrilansersoknad(
        soknadFraBasen: Sykepengesoknad,
        soknadMedOppdateringer: Sykepengesoknad,
        sporsmal: Sporsmal
    ): Sykepengesoknad {
        if (harEndretSvarPaArbeidGjenopptatt(soknadMedOppdateringer, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaArbeidGjenopptattSelvstendig(soknadMedOppdateringer))
        } else if (harEndretSvarPaBrukteReisetilskuddet(soknadMedOppdateringer, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaBrukteReisetilskuddet(soknadMedOppdateringer))
        } else {
            svarDAO.overskrivSvar(listOf(sporsmal).flatten())
        }
        return sykepengesoknadDAO.finnSykepengesoknad(soknadMedOppdateringer.id)
    }

    private fun oppdaterSporsmalArbeidsledigOgAnnetsoknad(
        soknadFraBasen: Sykepengesoknad,
        soknadMedOppdateringer: Sykepengesoknad,
        sporsmal: Sporsmal,
    ): Sykepengesoknad {
        if (harEndretSvarPaFriskmeldt(soknadMedOppdateringer, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaFriskmeldingSporsmal(soknadMedOppdateringer))
        } else if (harEndretSvarPaBrukteReisetilskuddet(soknadMedOppdateringer, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaBrukteReisetilskuddet(soknadMedOppdateringer))
        } else {
            svarDAO.overskrivSvar(listOf(sporsmal).flatten())
        }

        return sykepengesoknadDAO.finnSykepengesoknad(soknadMedOppdateringer.id)
    }

    private fun oppdaterSporsmalUtlandssoknad(
        soknadMedOppdateringer: Sykepengesoknad,
        sporsmal: Sporsmal
    ): Sykepengesoknad {
        var soknad = soknadMedOppdateringer
        soknad = soknad.replaceSporsmal(getOppdatertBekreftSporsmal(soknad))
        sporsmalDAO.oppdaterSporsmalstekst(soknad.getSporsmalMedTag(BEKREFT_OPPLYSNINGER_UTLAND_INFO))
        sporsmalDAO.oppdaterSporsmalstekst(soknad.getSporsmalMedTag(BEKREFT_OPPLYSNINGER_UTLAND))
        svarDAO.overskrivSvar(listOf(sporsmal).flatten())
        return sykepengesoknadDAO.finnSykepengesoknad(soknad.id)
    }

    private fun oppdaterSporsmalUtenLogikk(
        soknadMedOppdateringer: Sykepengesoknad,
        sporsmal: Sporsmal
    ): Sykepengesoknad {
        svarDAO.overskrivSvar(listOf(sporsmal).flatten())
        return sykepengesoknadDAO.finnSykepengesoknad(soknadMedOppdateringer.id)
    }

    private fun harNyttRelevantSvar(
        sykepengesoknad: Sykepengesoknad,
        soknadFraBasen: Sykepengesoknad,
        tag: String,
        relevantSvarVerdi: String = "NEI"
    ): Boolean {
        val optionalInnsendtSporsmal = sykepengesoknad.getOptionalSporsmalMedTag(tag)
        val optionalLagretSporsmal = soknadFraBasen.getOptionalSporsmalMedTag(tag)

        if (!optionalInnsendtSporsmal.isPresent && !optionalLagretSporsmal.isPresent) {
            return false
        } else if (optionalInnsendtSporsmal.isPresent != optionalLagretSporsmal.isPresent) {
            return true
        }

        val innsendtSporsmal = optionalInnsendtSporsmal.get()
        val lagretSporsmal = optionalLagretSporsmal.get()
        if (lagretSporsmal.svar.firstOrNull() == null &&
            relevantSvarVerdi == innsendtSporsmal.svar.firstOrNull()?.verdi
        ) {
            return false
        }

        return innsendtSporsmal.svar.map { it.verdi } != lagretSporsmal.svar.map { it.verdi }
    }

    private fun harEndretSvarPaFriskmeldt(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad): Boolean {

        return harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, FRISKMELDT, relevantSvarVerdi = "JA") ||
            harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, FRISKMELDT_START)
    }

    private fun harEndretSvarPaArbeidGjenopptatt(
        sykepengesoknad: Sykepengesoknad,
        soknadFraBasen: Sykepengesoknad
    ): Boolean {
        return harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, TILBAKE_I_ARBEID) ||
            harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, TILBAKE_NAR)
    }

    private fun harEndretSvarPaBrukteReisetilskuddet(
        sykepengesoknad: Sykepengesoknad,
        soknadFraBasen: Sykepengesoknad,
    ): Boolean {
        return harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, BRUKTE_REISETILSKUDDET)
    }

    private fun harEndretSvarPaFeriePermisjonUtland(
        sykepengesoknad: Sykepengesoknad,
        lagretSoknad: Sykepengesoknad
    ): Boolean {
        return if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad)) {
            (
                harNyttRelevantSvar(sykepengesoknad, lagretSoknad, FERIE_PERMISJON_UTLAND) ||
                    harNyttRelevantSvar(sykepengesoknad, lagretSoknad, FERIE) ||
                    harNyttRelevantSvar(sykepengesoknad, lagretSoknad, FERIE_NAR) ||
                    harNyttRelevantSvar(sykepengesoknad, lagretSoknad, UTLAND) ||
                    harNyttRelevantSvar(sykepengesoknad, lagretSoknad, UTLAND_NAR)
                )
        } else {
            (
                harNyttRelevantSvar(sykepengesoknad, lagretSoknad, FERIE_V2) ||
                    harNyttRelevantSvar(sykepengesoknad, lagretSoknad, FERIE_NAR_V2) ||
                    harNyttRelevantSvar(sykepengesoknad, lagretSoknad, UTLAND_V2) ||
                    harNyttRelevantSvar(sykepengesoknad, lagretSoknad, UTLAND_NAR_V2)
                )
        }
    }

    // Arbeidstaker legger til et nytt spørsmål hvis dette er sant
    // Frilanser/Selvstendig har dette som et underspørsmål tag:UTLAND
    private fun harSoktOmSykepengerIUtlandetSomUndersporsmal(sykepengesoknad: Sykepengesoknad): Boolean {
        return sykepengesoknad.alleSporsmalOgUndersporsmal()
            .any { spm ->
                spm.tag == UTLANDSOPPHOLD_SOKT_SYKEPENGER
            } &&
            sykepengesoknad.sporsmal.none { spm -> spm.tag == UTLANDSOPPHOLD_SOKT_SYKEPENGER }
    }

    fun lagreNyttSvar(lagretSoknad: Sykepengesoknad, sporsmalId: String, svar: Svar): OppdaterSporsmalResultat {
        val soknadId = lagretSoknad.id

        val sporsmal = (
            lagretSoknad.sporsmal.flatten()
                .find { it.id == sporsmalId }
                ?: throw IllegalArgumentException("$sporsmalId finnes ikke i søknad $soknadId")
            )

        val oppdatertSporsmal = sporsmal.copy(svar = sporsmal.svar.toMutableList().also { it.add(svar) })

        return oppdaterSporsmal(lagretSoknad, oppdatertSporsmal)
    }

    fun slettSvar(lagretSoknad: Sykepengesoknad, sporsmalId: String, svarId: String) {
        val soknadId = lagretSoknad.id
        val sporsmal = lagretSoknad.sporsmal
            .flatten()
            .find { it.id == sporsmalId }
            ?: throw IllegalArgumentException("Spørsmål $sporsmalId finnes ikke i søknad $soknadId.")

        val svarSomSkalFjernes = sporsmal.svar.find { it.id == svarId }
            ?: throw IllegalArgumentException("Svar $svarId finnes ikke i spørsmål $sporsmalId og søknad $soknadId.")

        // Spesialhåndterer sletting av kvitteringer da frontendkoden forventer at man skal kunne slette flere
        // kvitteringer uten å laste søknaden på nytt. oppdaterSporsmal() sletter alle svar tilhørende spørsmålet
        // og lagrer de på nytt, noe som fører til at de får ny id som frontend ikke kjenner til.
        if (sporsmal.tag == KVITTERINGER) {
            slettKvittering(sporsmal, svarSomSkalFjernes)
            log.info("Slettet kvittering med svarId $svarId fra spørsmål $sporsmalId tilhørende søknad $soknadId.")
        } else {
            val oppdatertSporsmal = sporsmal.copy(svar = sporsmal.svar - svarSomSkalFjernes)
            oppdaterSporsmal(lagretSoknad, oppdatertSporsmal)
        }
        log.info("Slettet svar $svarId for spørsmål $sporsmalId og søknad $soknadId.")
    }

    private fun slettKvittering(sporsmal: Sporsmal, svar: Svar) {
        svarDAO.slettSvar(sporsmal.id!!, svar.id!!)
        sykepengesoknadKvitteringerClient.slettKvittering(svar.verdi.tilKvittering().blobId)
    }
}
