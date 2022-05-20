package no.nav.syfo.service

import no.nav.syfo.client.bucketuploader.BucketUploaderClient
import no.nav.syfo.domain.*
import no.nav.syfo.domain.Arbeidssituasjon.*
import no.nav.syfo.domain.Soknadstype.*
import no.nav.syfo.domain.Soknadstype.ARBEIDSLEDIG
import no.nav.syfo.logger
import no.nav.syfo.repository.SporsmalDAO
import no.nav.syfo.repository.SvarDAO
import no.nav.syfo.repository.SykepengesoknadDAO
import no.nav.syfo.soknadsopprettelse.*
import no.nav.syfo.soknadsopprettelse.sporsmal.oppdaterMedSvarPaFriskmeldingSporsmal
import no.nav.syfo.soknadsopprettelse.sporsmal.oppdaterMedSvarPaaPermittertNaa
import no.nav.syfo.svarvalidering.tilKvittering
import no.nav.syfo.svarvalidering.validerSvarPaSporsmal
import no.nav.syfo.util.Metrikk
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
            ?: throw IllegalArgumentException("Soknad fra basen skal ha spørsmålet")

        if (listOf(sporsmal).erUlikUtenomSvar(listOf(sporsmaletFraBasen))) {
            throw IllegalArgumentException("Spørsmål i databasen er ulikt spørsmål som er besvart")
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
            // TODO generaliser mer!
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
                    else -> throw IllegalStateException("Arbeidssituasjon ${soknadMedOppdateringer.arbeidssituasjon} skal ikke kunne ha gradert reisetilskudd")
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
        } else if (harEndretSvarPaPermittertNaa(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaPermittertNaa(soknadMedResattSvar))
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
        } else if (harEndretSvarPaPermittertNaa(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaPermittertNaa(soknadMedResattSvar))
        } else if (harEndretSvarPaBrukteReisetilskuddet(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaBrukteReisetilskuddet(soknadMedResattSvar))
        } else {
            svarDAO.overskrivSvar(soknadMedOppdateringer)
        }
        return sykepengesoknadDAO.finnSykepengesoknad(soknadMedOppdateringer.id)
    }

    private fun oppdaterSporsmalArbeidsledigOgAnnetsoknad(soknadFraBasen: Sykepengesoknad, soknadMedOppdateringer: Sykepengesoknad): Sykepengesoknad {
        val soknadMedAvgittAv = beholdAvgittAv(soknadMedOppdateringer, soknadFraBasen)
        val soknadMedResattSvar = resettAvgittAvPaSvarSomErEndret(soknadMedAvgittAv, soknadFraBasen)
        if (harEndretSvarPaFriskmeldt(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaFriskmeldingSporsmal(soknadMedResattSvar))
        } else if (harEndretSvarPaPermittertNaa(soknadMedResattSvar, soknadFraBasen)) {
            sykepengesoknadDAO.byttUtSporsmal(oppdaterMedSvarPaaPermittertNaa(soknadMedResattSvar))
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

    private fun resettAvgittAvPaSvarSomErEndret(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad): Sykepengesoknad {
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

    private fun harNyttRelevantSvar(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad, tag: String, relevantSvarVerdi: String = "NEI"): Boolean {
        val optionalInnsendtSporsmal = sykepengesoknad.getOptionalSporsmalMedTag(tag)
        val optionalLagretSporsmal = soknadFraBasen.getOptionalSporsmalMedTag(tag)

        if (!optionalInnsendtSporsmal.isPresent && !optionalLagretSporsmal.isPresent) {
            return false
        } else if (optionalInnsendtSporsmal.isPresent != optionalLagretSporsmal.isPresent) {
            return true
        }

        val innsendtSporsmal = optionalInnsendtSporsmal.get()
        val lagretSporsmal = optionalLagretSporsmal.get()
        if (lagretSporsmal.svar.firstOrNull() == null && relevantSvarVerdi == innsendtSporsmal.svar.firstOrNull()?.verdi) {
            return false
        }

        return innsendtSporsmal.svar.map { it.verdi } != lagretSporsmal.svar.map { it.verdi }
    }

    private fun harEndretSvarPaFriskmeldt(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad): Boolean {

        return harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, FRISKMELDT, relevantSvarVerdi = "JA") ||
            harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, FRISKMELDT_START)
    }

    private fun harEndretSvarPaArbeidGjenopptatt(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad): Boolean {
        return harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, TILBAKE_I_ARBEID) ||
            harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, TILBAKE_NAR)
    }

    private fun harEndretSvarPaPermittertNaa(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad): Boolean {
        return harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, PERMITTERT_NAA)
    }

    private fun harEndretSvarPaBrukteReisetilskuddet(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad): Boolean {
        return harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, BRUKTE_REISETILSKUDDET)
    }

    private fun harEndretSvarPaFeriePermisjonUtland(sykepengesoknad: Sykepengesoknad, soknadFraBasen: Sykepengesoknad): Boolean {
        return if (harFeriePermisjonEllerUtenlandsoppholdSporsmal(sykepengesoknad)) {
            (
                harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, FERIE_PERMISJON_UTLAND) ||
                    harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, FERIE) ||
                    harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, FERIE_NAR) ||
                    harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, UTLAND) ||
                    harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, UTLAND_NAR)
                )
        } else {
            (
                harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, FERIE_V2) ||
                    harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, FERIE_NAR_V2) ||
                    harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, UTLAND_V2) ||
                    harNyttRelevantSvar(sykepengesoknad, soknadFraBasen, UTLAND_NAR_V2)
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

    fun lagreNyttSvar(
        soknadFraBasenFørOppdatering: Sykepengesoknad,
        sporsmalId: String,
        svar: Svar
    ): OppdaterSporsmalResultat {
        val soknadId = soknadFraBasenFørOppdatering.id

        val sporsmal = (
            soknadFraBasenFørOppdatering.sporsmal.flatten()
                .find { it.id == sporsmalId }
                ?: throw IllegalArgumentException("$sporsmalId finnes ikke i søknad $soknadId")
            )

        val oppdatertSporsmal = sporsmal.copy(svar = sporsmal.svar.toMutableList().also { it.add(svar) })

        return oppdaterSporsmal(soknadFraBasenFørOppdatering, oppdatertSporsmal)
    }

    fun slettSvar(soknadFraBasenFørOppdatering: Sykepengesoknad, sporsmalId: String, svarId: String) {
        val soknadId = soknadFraBasenFørOppdatering.id
        val sporsmal = soknadFraBasenFørOppdatering.sporsmal
            .flatten()
            .find { it.id == sporsmalId }
            ?: throw IllegalArgumentException("Spørsmål $sporsmalId finnes ikke i søknad $soknadId.")

        val svarSomSkalFjernes = sporsmal.svar.find { it.id == svarId }
            ?: throw IllegalArgumentException("Svar $svarId finnes ikke i spørsmål $sporsmalId og søknad $soknadId.")

        // Spesialhåndterer sletting av kvitteringer da frontendkoden forventer at man skal kunne slette flere
        // kvitteringer uten å laste søknaden på nytt. oppdaterSporsmal() sletter alle svar og lagrer de på nytt, noe
        // som fører til at de får ny Id som ikke blir hentet av frontend.
        if (sporsmal.tag == KVITTERINGER) {
            log.info("Sletter kvittering med svarId $svarId fra spørsmål $sporsmalId tilhørende søknad $soknadId.")
            slettKvittering(sporsmal, svarSomSkalFjernes, soknadId)
        } else {
            val oppdatertSporsmal = sporsmal.copy(svar = sporsmal.svar - svarSomSkalFjernes)
            oppdaterSporsmal(soknadFraBasenFørOppdatering, oppdatertSporsmal)
        }
        log.info("Slettet svar $svarId for spørsmål $sporsmalId og søknad $soknadId.")
    }

    private fun slettKvittering(sporsmal: Sporsmal, svar: Svar, soknadId: String) {
        svarDAO.slettSvar(sporsmal.id!!, svar.id!!)

        val blobId = svar.verdi.tilKvittering().blobId
        val slettKvittering = bucketUploaderClient.slettKvittering(blobId)
        if (!slettKvittering) {
            log.warn("Sletting av blobId $blobId fra bucket for søknad $soknadId feilet. Vedlegget er slettet fra databasen.")
        }
    }
}
