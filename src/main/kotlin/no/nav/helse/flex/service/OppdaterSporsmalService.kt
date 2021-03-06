package no.nav.helse.flex.service

import no.nav.helse.flex.client.bucketuploader.BucketUploaderClient
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
    val bucketUploaderClient: BucketUploaderClient,
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
            ?: throw IllegalArgumentException("Soknad fra basen skal ha sp??rsm??let")

        if (listOf(sporsmal).erUlikUtenomSvar(listOf(sporsmaletFraBasen))) {
            throw IllegalArgumentException("Sp??rsm??l i databasen er ulikt sp??rsm??l som er besvart")
        }

        sporsmal.validerSvarPaSporsmal()

        val soknadMedOppdatertSporsmal = soknadFraBasenForOppdatering.replaceSporsmal(sporsmal)

        val oppdatertSoknad = oppdaterSoknad(soknadFraBasenForOppdatering, soknadMedOppdatertSporsmal)

        val soknadenErMutert = soknadFraBasenForOppdatering.sporsmal.erUlikUtenomSvar(oppdatertSoknad.sporsmal)

        return OppdaterSporsmalResultat(oppdatertSoknad, soknadenErMutert)
    }

    fun oppdaterSoknad(soknadFraBasen: Sykepengesoknad, soknadMedOppdateringer: Sykepengesoknad): Sykepengesoknad {
        return when (soknadMedOppdateringer.soknadstype) {
            ARBEIDSTAKERE -> {
                oppdaterSporsmalArbeidstakersoknad(
                    soknadFraBasen = soknadFraBasen,
                    soknadMedOppdateringer = soknadMedOppdateringer
                )
            }
            SELVSTENDIGE_OG_FRILANSERE -> {
                oppdaterSporsmalSelvstendigFrilansersoknad(
                    soknadFraBasen = soknadFraBasen,
                    soknadMedOppdateringer = soknadMedOppdateringer
                )
            }
            ARBEIDSLEDIG, ANNET_ARBEIDSFORHOLD -> {
                oppdaterSporsmalArbeidsledigOgAnnetsoknad(
                    soknadFraBasen = soknadFraBasen,
                    soknadMedOppdateringer = soknadMedOppdateringer
                )
            }
            GRADERT_REISETILSKUDD -> {
                when (soknadMedOppdateringer.arbeidssituasjon) {
                    ARBEIDSTAKER -> {
                        oppdaterSporsmalArbeidstakersoknad(
                            soknadFraBasen = soknadFraBasen,
                            soknadMedOppdateringer = soknadMedOppdateringer
                        )
                    }
                    FRILANSER, NAERINGSDRIVENDE -> {
                        oppdaterSporsmalSelvstendigFrilansersoknad(
                            soknadFraBasen = soknadFraBasen,
                            soknadMedOppdateringer = soknadMedOppdateringer
                        )
                    }
                    Arbeidssituasjon.ARBEIDSLEDIG, ANNET -> {
                        oppdaterSporsmalArbeidsledigOgAnnetsoknad(
                            soknadFraBasen = soknadFraBasen,
                            soknadMedOppdateringer = soknadMedOppdateringer
                        )
                    }
                    else -> throw IllegalStateException(
                        "Arbeidssituasjon ${soknadMedOppdateringer.arbeidssituasjon} skal ikke kunne ha gradert " +
                            "reisetilskudd"
                    )
                }
            }
            OPPHOLD_UTLAND -> {
                oppdaterSporsmalUtlandssoknad(soknadMedOppdateringer = soknadMedOppdateringer)
            }
            BEHANDLINGSDAGER -> {
                oppdaterSporsmalUtenLogikk(soknadMedOppdateringer = soknadMedOppdateringer)
            }
            REISETILSKUDD -> {
                oppdaterSporsmalUtenLogikk(soknadMedOppdateringer = soknadMedOppdateringer)
            }
        }
    }

    private fun oppdaterSporsmalArbeidstakersoknad(
        soknadFraBasen: Sykepengesoknad,
        soknadMedOppdateringer: Sykepengesoknad
    ): Sykepengesoknad {
        val soknadMedAvgittAv = beholdAvgittAv(soknadMedOppdateringer, soknadFraBasen)
        val soknadMedResattSvar = resettAvgittAvPaSvarSomErEndret(soknadMedAvgittAv, soknadFraBasen)

        if (harEndretSvarPaArbeidGjenopptatt(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaArbeidGjenopptattArbeidstaker(soknadMedResattSvar))
        } else if (harEndretSvarPaFeriePermisjonUtland(
                soknadMedResattSvar,
                soknadFraBasen
            ) || harSoktOmSykepengerIUtlandetSomUndersporsmal(soknadMedResattSvar)
        ) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaUtlandsopphold(soknadMedResattSvar))
        } else if (harEndretSvarPaBrukteReisetilskuddet(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaBrukteReisetilskuddet(soknadMedResattSvar))
        } else {
            svarDAO.overskrivSvar(soknadMedResattSvar)
        }
        return sykepengesoknadDAO.finnSykepengesoknad(soknadMedResattSvar.id)
    }

    private fun oppdaterSporsmalSelvstendigFrilansersoknad(
        soknadFraBasen: Sykepengesoknad,
        soknadMedOppdateringer: Sykepengesoknad
    ): Sykepengesoknad {
        val soknadMedAvgittAv = beholdAvgittAv(soknadMedOppdateringer, soknadFraBasen)
        val soknadMedResattSvar = resettAvgittAvPaSvarSomErEndret(soknadMedAvgittAv, soknadFraBasen)

        if (harEndretSvarPaArbeidGjenopptatt(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaArbeidGjenopptattSelvstendig(soknadMedResattSvar))
        } else if (harEndretSvarPaBrukteReisetilskuddet(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaBrukteReisetilskuddet(soknadMedResattSvar))
        } else {
            svarDAO.overskrivSvar(soknadMedOppdateringer)
        }
        return sykepengesoknadDAO.finnSykepengesoknad(soknadMedOppdateringer.id)
    }

    private fun oppdaterSporsmalArbeidsledigOgAnnetsoknad(
        soknadFraBasen: Sykepengesoknad,
        soknadMedOppdateringer: Sykepengesoknad
    ): Sykepengesoknad {
        val soknadMedAvgittAv = beholdAvgittAv(soknadMedOppdateringer, soknadFraBasen)
        val soknadMedResattSvar = resettAvgittAvPaSvarSomErEndret(soknadMedAvgittAv, soknadFraBasen)
        if (harEndretSvarPaFriskmeldt(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaFriskmeldingSporsmal(soknadMedResattSvar))
        } else if (harEndretSvarPaBrukteReisetilskuddet(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaBrukteReisetilskuddet(soknadMedResattSvar))
        } else {
            svarDAO.overskrivSvar(soknadMedOppdateringer)
        }

        return sykepengesoknadDAO.finnSykepengesoknad(soknadMedOppdateringer.id)
    }

    private fun oppdaterSporsmalUtlandssoknad(soknadMedOppdateringer: Sykepengesoknad): Sykepengesoknad {
        var soknad = soknadMedOppdateringer
        soknad = soknad.replaceSporsmal(getOppdatertBekreftSporsmal(soknad))
        sporsmalDAO.oppdaterSporsmalstekst(soknad.getSporsmalMedTag(BEKREFT_OPPLYSNINGER_UTLAND_INFO))
        sporsmalDAO.oppdaterSporsmalstekst(soknad.getSporsmalMedTag(BEKREFT_OPPLYSNINGER_UTLAND))
        svarDAO.overskrivSvar(soknad)
        return sykepengesoknadDAO.finnSykepengesoknad(soknad.id)
    }

    private fun oppdaterSporsmalUtenLogikk(soknadMedOppdateringer: Sykepengesoknad): Sykepengesoknad {

        svarDAO.overskrivSvar(soknadMedOppdateringer)

        return sykepengesoknadDAO.finnSykepengesoknad(soknadMedOppdateringer.id)
    }

    private fun beholdAvgittAv(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad): Sykepengesoknad {
        val nyeSporsmal = sykepengesoknad.alleSporsmalOgUndersporsmal()
            .filter { sporsmal ->
                soknadFraBasen
                    .getSporsmalMedTag(sporsmal.tag)
                    .svar
                    .any { svar -> svar.avgittAv != null }
            }
            .map { sporsmal ->
                sporsmal.copy(
                    svar = sporsmal
                        .svar
                        .map { svar ->
                            svar.copy(
                                avgittAv = soknadFraBasen
                                    .getSporsmalMedTag(sporsmal.tag)
                                    .svar
                                    .mapNotNull { it.avgittAv }
                                    .firstOrNull()
                            )
                        }
                )
            }
        @Suppress("DEPRECATION")
        return sykepengesoknad.replaceSporsmal(nyeSporsmal)
    }

    private fun resettAvgittAvPaSvarSomErEndret(
        sykepengesoknad: Sykepengesoknad,
        soknadFraBasen: Sykepengesoknad
    ): Sykepengesoknad {
        val nyeSporsmal = sykepengesoknad.alleSporsmalOgUndersporsmal()
            .filterNot { sporsmal ->
                soknadFraBasen.getSporsmalMedTag(sporsmal.tag).svar == sporsmal.svar
            }
            .map { sporsmal ->
                sporsmal.copy(
                    svar = sporsmal
                        .svar
                        .map { svar ->
                            svar.copy(avgittAv = null)
                        }
                )
            }
        @Suppress("DEPRECATION")
        return sykepengesoknad.replaceSporsmal(nyeSporsmal)
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

    private fun harEndretSvarPaBrukteReisetilskuddet(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad): Boolean {
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

    // Arbeidstaker legger til et nytt sp??rsm??l hvis dette er sant
    // Frilanser/Selvstendig har dette som et undersp??rsm??l tag:UTLAND
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
                ?: throw IllegalArgumentException("$sporsmalId finnes ikke i s??knad $soknadId")
            )

        val oppdatertSporsmal = sporsmal.copy(svar = sporsmal.svar.toMutableList().also { it.add(svar) })

        return oppdaterSporsmal(lagretSoknad, oppdatertSporsmal)
    }

    fun slettSvar(lagretSoknad: Sykepengesoknad, sporsmalId: String, svarId: String) {
        val soknadId = lagretSoknad.id
        val sporsmal = lagretSoknad.sporsmal
            .flatten()
            .find { it.id == sporsmalId }
            ?: throw IllegalArgumentException("Sp??rsm??l $sporsmalId finnes ikke i s??knad $soknadId.")

        val svarSomSkalFjernes = sporsmal.svar.find { it.id == svarId }
            ?: throw IllegalArgumentException("Svar $svarId finnes ikke i sp??rsm??l $sporsmalId og s??knad $soknadId.")

        // Spesialh??ndterer sletting av kvitteringer da frontendkoden forventer at man skal kunne slette flere
        // kvitteringer uten ?? laste s??knaden p?? nytt. oppdaterSporsmal() sletter alle svar og lagrer de p?? nytt, noe
        // som f??rer til at de f??r ny Id som ikke blir hentet av frontend.
        if (sporsmal.tag == KVITTERINGER) {
            slettKvittering(sporsmal, svarSomSkalFjernes)
            log.info("Slettet kvittering med svarId $svarId fra sp??rsm??l $sporsmalId tilh??rende s??knad $soknadId.")
        } else {
            val oppdatertSporsmal = sporsmal.copy(svar = sporsmal.svar - svarSomSkalFjernes)
            oppdaterSporsmal(lagretSoknad, oppdatertSporsmal)
        }
        log.info("Slettet svar $svarId for sp??rsm??l $sporsmalId og s??knad $soknadId.")
    }

    private fun slettKvittering(sporsmal: Sporsmal, svar: Svar) {
        svarDAO.slettSvar(sporsmal.id!!, svar.id!!)
        bucketUploaderClient.slettKvittering(svar.verdi.tilKvittering().blobId)
    }
}
