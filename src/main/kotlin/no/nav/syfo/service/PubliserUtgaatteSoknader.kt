package no.nav.syfo.service

import io.micrometer.core.instrument.MeterRegistry
import no.nav.syfo.config.EnvironmentToggles
import no.nav.syfo.cronjob.LeaderElection
import no.nav.syfo.kafka.producer.SoknadProducer
import no.nav.syfo.logger
import no.nav.syfo.repository.SykepengesoknadDAO
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

@Component
class PubliserUtgaatteSoknader(
    val soknadProducer: SoknadProducer,
    val toggle: EnvironmentToggles,
    val leaderElection: LeaderElection,
    val sykepengesoknadDAO: SykepengesoknadDAO,
    registry: MeterRegistry,
) {
    val log = logger()
    val counter = registry.counter("publisert_utgatt_soknad_pa_kafka")

    fun publiserUtgatteSoknader(): Int {
        val soknaderTilPublisering = sykepengesoknadDAO.finnUpubliserteUtlopteSoknader()
        soknaderTilPublisering.forEach {
            val soknadUtenSporsmal = sykepengesoknadDAO.finnSykepengesoknad(it).copy(sporsmal = emptyList())
            soknadProducer.soknadEvent(soknadUtenSporsmal)
            sykepengesoknadDAO.settUtloptPublisert(it, LocalDateTime.now())
            counter.increment()
        }
        if (soknaderTilPublisering.isNotEmpty()) {
            log.info("Publiserte ${soknaderTilPublisering.size} utgåtte søknader på kafka")
        }
        return soknaderTilPublisering.size
    }

    @Scheduled(fixedDelay = 1, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (toggle.isNotProduction() || erPåNatta()) {
            if (leaderElection.isLeader()) {
                publiserUtgatteSoknader()
            }
        }
    }

    fun erPåNatta(): Boolean {
        val osloTid = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("Europe/Oslo"))
        if (osloTid.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return false
        return osloTid.hour in 1..2 // 1:00 -> 01:59
    }
}
