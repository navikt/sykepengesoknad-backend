package no.nav.helse.flex.cronjob

import no.nav.helse.flex.service.DeaktiverGamleSoknaderService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

private const val ANTALL_SOKNADER = 100

@Component
class DeaktiveringJob(
    val deaktiverGamleSoknaderService: DeaktiverGamleSoknaderService,
    val leaderElection: LeaderElection,
) {
    @Scheduled(initialDelay = 5, fixedDelay = 60, timeUnit = TimeUnit.MINUTES)
    fun deaktiveringJob() {
        if (leaderElection.isLeader()) {
            deaktiverGamleSoknaderService.deaktiverSoknader()
        }
    }

    @Scheduled(initialDelay = 180, fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    fun slettFraDeaktiverteSoknaderJob() {
        if (leaderElection.isLeader()) {
            deaktiverGamleSoknaderService.slettSporsmalOgSvarFraDeaktiverteSoknader(ANTALL_SOKNADER)
        }
    }
}
