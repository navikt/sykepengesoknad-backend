package no.nav.helse.flex.service

import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
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
                // TODO: Slår av publisering av utgåtte søknader til vi er i sync med det som er publisert i fra syfosoknad
                // publiserUtgatteSoknader()
            }
        }
    }

    fun erPåNatta(): Boolean {
        val osloTid = LocalDateTime.ofInstant(Instant.now(), ZoneId.of("Europe/Oslo"))
        if (osloTid.dayOfWeek in listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)) return false
        return osloTid.hour in 1..2 // 1:00 -> 02:59
    }

    @Scheduled(fixedDelay = 10_000, initialDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun oppdaterUtloptPublisert() {
        if (leaderElection.isLeader()) {
            val antall = sykepengesoknadDAO.finnUpubliserteUtlopteSoknaderSomErBehandletISyfosoknad()

            log.info("Oppdaterer utlopt_publisert for $antall søknader")
        }
    }
}
