package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class SyfoSoknadJob(
    val leaderElection: LeaderElection
) {
    val log = logger()

    @Scheduled(cron = "\${syfosoknad.cron}")
    fun run() {
        if (leaderElection.isLeader()) {
            log.info("Kjører syfosoknadjob")

            log.info("Ferdig med syfosoknadjob")
        }
    }
}
