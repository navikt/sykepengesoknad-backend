package no.nav.helse.flex.cronjob

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.AutomatiskInnsendingVedDodsfallService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate

@Component
class SyfoSoknadJob(
    val automatiskInnsendingVedDodsfallService: AutomatiskInnsendingVedDodsfallService,
    val toggle: EnvironmentToggles,
    val leaderElection: LeaderElection
) {
    val log = logger()

    @Scheduled(cron = "\${syfosoknad.cron}")
    fun run() {
        if (leaderElection.isLeader()) {

            log.info("Kjører syfosoknadjob")

            if (toggle.isNotProduction() || LocalDate.now().dayOfWeek == DayOfWeek.WEDNESDAY) {
                automatiskInnsendingVedDodsfallService.sendSoknaderForDode()
            } else {
                log.info("Prosesserer dødsmeldinger på onsdager")
            }

            log.info("Ferdig med syfosoknadjob")
        }
    }
}
