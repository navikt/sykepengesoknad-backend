package no.nav.helse.flex.service

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
) {
    val log = logger()

    fun publiserUtgatteSoknader(): Int {
        val soknaderTilPublisering = sykepengesoknadDAO.finnUpubliserteUtlopteSoknader()
        soknaderTilPublisering.forEach {
            val soknad = sykepengesoknadDAO.finnSykepengesoknad(it)
            soknadProducer.soknadEvent(soknad)
            sykepengesoknadDAO.settUtloptPublisert(it, LocalDateTime.now())
        }
        if (soknaderTilPublisering.isNotEmpty()) {
            log.info("Publiserte ${soknaderTilPublisering.size} utgåtte søknader på Kafka.")
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
        return osloTid.hour in 1..2 // 1:00 -> 02:59
    }
}
