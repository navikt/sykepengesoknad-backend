package no.nav.helse.flex.cronjob

import no.nav.helse.flex.service.SlettGamleUtkastService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class GamleUtkastJob(
    val slettGamleUtkastService: SlettGamleUtkastService,
    val leaderElection: LeaderElection,
) {
    @Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun gamleUtkastJob() {
        if (leaderElection.isLeader()) {
            slettGamleUtkastService.slettGamleUtkast()
        }
    }
}
