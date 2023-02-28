package no.nav.helse.flex.personhendelse

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.util.osloZone
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class AutomatiskInnsendingJob(
    val automatiskInnsendingVedDodsfall: AutomatiskInnsendingVedDodsfall,
    val leaderElection: LeaderElection,
    val env: EnvironmentToggles
) {

    @Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun automatiskUtsending() {
        if (leaderElection.isLeader()) {
            if (env.isNotProduction() || LocalDate.now(osloZone).dayOfWeek == DayOfWeek.WEDNESDAY) {
                automatiskInnsendingVedDodsfall.sendSoknaderForDode()
            }
        }
    }
}
