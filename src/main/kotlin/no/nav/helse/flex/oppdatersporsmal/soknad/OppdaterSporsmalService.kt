package no.nav.helse.flex.oppdatersporsmal.soknad

import no.nav.helse.flex.client.kvitteringer.SykepengesoknadKvitteringerClient
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.flatten
import no.nav.helse.flex.logger
import no.nav.helse.flex.oppdatersporsmal.soknad.muteringer.arbeidGjenopptattMutering
import no.nav.helse.flex.oppdatersporsmal.soknad.muteringer.brukteDuReisetilskuddetMutering
import no.nav.helse.flex.oppdatersporsmal.soknad.muteringer.friskmeldtMuteringer
import no.nav.helse.flex.oppdatersporsmal.soknad.muteringer.jobbaDuHundreGate
import no.nav.helse.flex.oppdatersporsmal.soknad.muteringer.oppdaterMedSvarPaUtlandsopphold
import no.nav.helse.flex.oppdatersporsmal.soknad.muteringer.utlandssoknadMuteringer
import no.nav.helse.flex.repository.SvarDAO
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge
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
    val svarDAO: SvarDAO,
    val metrikk: Metrikk
) {
    val log = logger()

    class OppdaterSporsmalResultat(val oppdatertSoknad: Sykepengesoknad, val mutert: Boolean)

    fun oppdaterSporsmal(soknadFraBasenForOppdatering: Sykepengesoknad, sporsmal: Sporsmal): OppdaterSporsmalResultat {
        val sporsmaletFraBasen = soknadFraBasenForOppdatering.sporsmal.find { it.id == sporsmal.id }
            ?: throw IllegalArgumentException("Soknad fra basen skal ha spørsmålet")

        if (listOf(sporsmal).erUlikUtenomSvar(listOf(sporsmaletFraBasen))) {
            throw IllegalArgumentException("Spørsmål i databasen er ulikt spørsmål som er besvart")
        }

        sporsmal.validerSvarPaSporsmal()

        val oppdatertSoknad = soknadFraBasenForOppdatering
            .replaceSporsmal(sporsmal)
            .jobbaDuHundreGate()
            .friskmeldtMuteringer()
            .brukteDuReisetilskuddetMutering()
            .utlandssoknadMuteringer()
            .arbeidGjenopptattMutering()
            .oppdaterMedSvarPaUtlandsopphold()

        val soknadenErMutert = soknadFraBasenForOppdatering.sporsmal.erUlikUtenomSvar(oppdatertSoknad.sporsmal)

        if (soknadenErMutert) {
            sykepengesoknadDAO.byttUtSporsmal(oppdatertSoknad)
        } else {
            svarDAO.overskrivSvar(listOf(sporsmal).flatten())
        }
        // Vi må returnerer oppdatert spørsmål når vi har lagret en kvittering sånn at den har en id hvis den blir
        // forsøket slettet uten at siden må lastes på nytt.
        val soknad = if (soknadenErMutert || sporsmal.tag == KVITTERINGER) {
            sykepengesoknadDAO.finnSykepengesoknad(oppdatertSoknad.id)
        } else {
            oppdatertSoknad
        }
        return OppdaterSporsmalResultat(soknad, soknadenErMutert)
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

    fun leggTilNyttUndersporsmal(soknadId: String, tag: String) {
        val soknad = sykepengesoknadDAO.finnSykepengesoknad(soknadId)
        soknad.sporsmal.first { it.tag == MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE }.let { sporsmal ->
            val oppdatertSporsmal = sporsmal.copy(
                undersporsmal = sporsmal.undersporsmal + lagGruppertUndersporsmalTilSporsmalOmArbeidUtenforNorge(
                    finnHoyesteIndex(sporsmal.undersporsmal) + 1
                )
            )
            sykepengesoknadDAO.byttUtSporsmal(soknad.replaceSporsmal(oppdatertSporsmal))
        }
    }

    fun slettUndersporsmal(soknad: Sykepengesoknad, hovedSporsmal: Sporsmal, undersporsmalId: String) {
        val oppdatertSporsmal = hovedSporsmal.copy(
            undersporsmal = hovedSporsmal.undersporsmal.filterNot { it.id == undersporsmalId }
        )
        sykepengesoknadDAO.byttUtSporsmal(soknad.replaceSporsmal(oppdatertSporsmal))
    }

    private fun slettKvittering(sporsmal: Sporsmal, svar: Svar) {
        svarDAO.slettSvar(sporsmal.id!!, svar.id!!)
        sykepengesoknadKvitteringerClient.slettKvittering(svar.verdi.tilKvittering().blobId)
    }
}
