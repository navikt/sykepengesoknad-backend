package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.KlippMetrikkRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class OverlappMetrikkJob(
    private val leaderElection: LeaderElection,
    private val klippMetrikkRepository: KlippMetrikkRepository
) {

    private val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun job() {
        if (leaderElection.isLeader()) {
            val duplikater = klippMetrikkRepository.findDuplikateMetrikker()

            log.info("Fant ${duplikater.size} duplikater")
            log.info("Fant ${duplikater.filter { it.variant == "SOKNAD_STARTER_INNI_SLUTTER_ETTER" }.size} SOKNAD_STARTER_INNI_SLUTTER_ETTER")
            log.info("Fant ${duplikater.filter { it.variant == "SOKNAD_STARTER_FOR_SLUTTER_ETTER" }.size} SOKNAD_STARTER_FOR_SLUTTER_ETTER")
            log.info("Fant ${duplikater.filter { it.variant == "SOKNAD_STARTER_FOR_SLUTTER_INNI" }.size} SOKNAD_STARTER_FOR_SLUTTER_INNI")

            // TODO: slett disse
        }
    }
}
