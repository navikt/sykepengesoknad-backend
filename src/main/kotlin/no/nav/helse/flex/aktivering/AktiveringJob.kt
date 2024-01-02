package no.nav.helse.flex.aktivering

import no.nav.helse.flex.aktivering.kafka.AktiveringBestilling
import no.nav.helse.flex.aktivering.kafka.AktiveringProducer
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

    @Scheduled(initialDelay = 5, fixedDelay = 120, timeUnit = TimeUnit.MINUTES)
    fun startBestillAktivering() {
        bestillAktivering()
    }

    fun bestillAktivering(now: LocalDate = LocalDate.now(osloZone)) {
        if (leaderElection.isLeader()) {
            val soknaderSomSkalAktiveres = sykepengesoknadRepository.finnSoknaderSomSkalAktiveres(now)

            if (soknaderSomSkalAktiveres.isEmpty()) {
                return
            }
            log.info("Publiserer ${soknaderSomSkalAktiveres.size} soknader som skal aktiveres på kafka ")
            val publiseringstid =
                measureTimeMillis {
                    soknaderSomSkalAktiveres.forEach {
                        aktiveringProducer.leggPaAktiveringTopic(
                            AktiveringBestilling(it.fnr, it.sykepengesoknadUuid),
                        )
                    }
                }

            log.info("Har publisert ${soknaderSomSkalAktiveres.size} soknader som skal aktiveres på kafka. Tid $publiseringstid")
        }
    }
}
