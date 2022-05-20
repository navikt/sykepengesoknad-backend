package no.nav.helse.flex.cronjob

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.AktiverService
import no.nav.helse.flex.service.AutomatiskInnsendingVedDodsfallService
import no.nav.helse.flex.service.DeaktiverGamleSoknaderService
import no.nav.helse.flex.service.SlettGamleUtkastService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate

@Component
class SyfoSoknadJob(
    val aktiverService: AktiverService,
    val deaktiverGamleSoknaderService: DeaktiverGamleSoknaderService,
    val automatiskInnsendingVedDodsfallService: AutomatiskInnsendingVedDodsfallService,
    val slettGamleUtkastService: SlettGamleUtkastService,
    val toggle: EnvironmentToggles,
    val leaderElection: LeaderElection

) {
    val log = logger()

    @Scheduled(cron = "\${syfosoknad.cron}")
    fun run() {
        if (leaderElection.isLeader()) {

            log.info("Kjører syfosoknadjob")

            aktiverService.aktiverSoknader()
            deaktiverGamleSoknaderService.deaktiverSoknader()
            slettGamleUtkastService.slettGamleUtkast()

            if (toggle.isNotProduction() || LocalDate.now().dayOfWeek == DayOfWeek.WEDNESDAY) {
                automatiskInnsendingVedDodsfallService.sendSoknaderForDode()
            } else {
                log.info("Prosesserer dødsmeldinger på onsdager")
            }

            log.info("Ferdig med syfosoknadjob")
        }
    }
}
