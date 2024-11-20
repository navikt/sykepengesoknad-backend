package no.nav.helse.flex.aktivering

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.util.osloZone
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

@Component
class AktiveringJob(
    val aktiveringProducer: AktiveringProducer,
    val leaderElection: LeaderElection,
    val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    fun startBestillAktivering() {
        if (leaderElection.isLeader()) {
            log.info("Kjører startBestillAktivering siden pod er leader.")
            bestillAktivering()
        } else {
            log.info("Kjører ikke startBestillAktivering siden pod ikke er leader.")
        }
    }

    fun bestillAktivering(now: LocalDate = LocalDate.now(osloZone)) {
        val skalAktiveres = sykepengesoknadRepository.finnSoknaderSomSkalAktiveres(now)
        if (skalAktiveres.isEmpty()) {
            return
        }
        val publiseringstid =
            measureTimeMillis {
                skalAktiveres.forEach {
                    aktiveringProducer.leggPaAktiveringTopic(AktiveringBestilling(it.fnr, it.sykepengesoknadUuid))
                }
            }
        log.info("Publiserte ${skalAktiveres.size} soknader som skal aktiveres på $publiseringstid millisekunder.")
    }
}

data class AktiveringBestilling(
    val fnr: String,
    val soknadId: String,
)
