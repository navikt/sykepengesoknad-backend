package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.aktivering.kafka.AktiveringBestilling
import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.forskuttering.ForskutteringRepository
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import no.nav.helse.flex.repository.JulesoknadkandidatDAO.Julesoknadkandidat
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.util.Metrikk
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

@Service
class ProsesserJulesoknadkandidater(
    private val metrikk: Metrikk,
    private val julesoknadkandidatDAO: JulesoknadkandidatDAO,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val leaderElection: LeaderElection,
    private val forskutteringRepository: ForskutteringRepository,
    private val aktiveringProducer: AktiveringProducer,
) {
    private val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 10, timeUnit = TimeUnit.MINUTES)
    fun prosseserJulesoknadKandidater() {
        if (leaderElection.isLeader()) {
            val julesoknadkandidater = julesoknadkandidatDAO.hentJulesoknadkandidater()
            if (julesoknadkandidater.isEmpty()) {
                return
            }
            log.info("Prosseserer ${julesoknadkandidater.size} julesoknadkandidater")
            julesoknadkandidater.forEach { julesoknadkandidat ->
                prosseserJulesoknadKandidat(julesoknadkandidat)
            }
        }
    }

    @Transactional
    fun prosseserJulesoknadKandidat(julesoknadkandidat: Julesoknadkandidat) {
        try {
            log.debug("Prosseserer julesoknadkandidat $julesoknadkandidat")

            val soknad = try {
                sykepengesoknadDAO.finnSykepengesoknad(julesoknadkandidat.sykepengesoknadUuid)
            } catch (e: SykepengesoknadDAO.SoknadIkkeFunnetException) {
                log.info("Julesøknadkandidat $julesoknadkandidat sin søknad er ikke lengre i db, sletter")
                julesoknadkandidatDAO.slettJulesoknadkandidat(julesoknadkandidat.julesoknadkandidatId)
                return
            }
            if (soknad.status != Soknadstatus.FREMTIDIG) {
                log.info("Julesøknadkandidat $julesoknadkandidat har ikke lengre status fremtidig, sletter")
                julesoknadkandidatDAO.slettJulesoknadkandidat(julesoknadkandidat.julesoknadkandidatId)
                return
            }
            val tidligereFremtidigeSoknader = sykepengesoknadDAO.finnSykepengesoknaderForSykmelding(
                sykmeldingId = soknad.sykmeldingId!!,
            )
                .filter { it.fom!!.isBefore(soknad.fom) }
                .filter { it.status == Soknadstatus.FREMTIDIG }

            if (tidligereFremtidigeSoknader.isNotEmpty()) {
                log.debug("$julesoknadkandidat har tidligere fremtidige søknader på samme sykmelding ")
                return
            }
            if (soknad.arbeidsgiverForskuttererIkke()) {
                log.info("Arbeidsgiver forskutterer ikke julesøknadkandidat $julesoknadkandidat, aktiverer søknad og sletter kandidat")

                aktiveringProducer.leggPaAktiveringTopic(AktiveringBestilling(soknad.fnr, soknad.id))
                if (soknad.opprettet!!.isBefore(OffsetDateTime.now().minusHours(1).toInstant())) {
                    metrikk.julesoknadAktivertNlEndret()
                } else {
                    metrikk.julesoknadOpprettet()
                }

                julesoknadkandidatDAO.slettJulesoknadkandidat(julesoknadkandidat.julesoknadkandidatId)
                return
            }
        } catch (e: Exception) {
            log.error("Feil ved prossesering av julesøknadkandidat $julesoknadkandidat", e)
        }
    }

    private fun Sykepengesoknad.arbeidsgiverForskuttererIkke(): Boolean {
        if (this.arbeidssituasjon == no.nav.helse.flex.domain.Arbeidssituasjon.ARBEIDSTAKER) {
            val orgnummer = this.arbeidsgiverOrgnummer ?: throw RuntimeException("Forventer orgnummer")

            val forskuttering = forskutteringRepository.finnForskuttering(
                brukerFnr = this.fnr,
                orgnummer = orgnummer
            )?.arbeidsgiverForskutterer
            if (forskuttering == true) {
                return false
            }
            return true
        }
        return true
    }
}
