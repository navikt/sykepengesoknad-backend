package no.nav.helse.flex.cronjob

import no.nav.helse.flex.service.AktiverService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class AktiveringJob(
    val aktiverService: AktiverService,
    val leaderElection: LeaderElection,
) {

    @Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun aktiveringJob() {
        if (leaderElection.isLeader()) {
            var aktiverte: Int

            do {
                aktiverte = aktiverService.aktiverSoknader()
            } while (aktiverte > 0 && leaderElection.isLeader())
        }
    }
}
