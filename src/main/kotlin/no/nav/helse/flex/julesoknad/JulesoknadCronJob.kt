package no.nav.helse.flex.julesoknad

import no.nav.helse.flex.ApplicationHealth
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.JulesoknadkandidatDAO
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class JulesoknadCronJob(
    private val julesoknadkandidatDAO: JulesoknadkandidatDAO,
    private val leaderElection: LeaderElection,
    private val applicationHealth: ApplicationHealth,
    private val prosesserJulesoknadkandidat: ProsesserJulesoknadkandidat,
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
                if (!applicationHealth.ok()) {
                    log.info("Stanser prosseserJulesoknadKandidat siden application state ikke er ok")
                    return
                }
                prosesserJulesoknadkandidat.prosseserJulesoknadKandidat(julesoknadkandidat)
            }
        }
    }
}
