package no.nav.helse.flex.cronjob

import no.nav.helse.flex.service.DeaktiverGamleSoknaderService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class DeaktiveringJob(
    val deaktiverGamleSoknaderService: DeaktiverGamleSoknaderService,
    val leaderElection: LeaderElection,
) {
    @Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun deaktiveringJob() {
        if (leaderElection.isLeader()) {
            deaktiverGamleSoknaderService.deaktiverSoknader()
        }
    }
}
