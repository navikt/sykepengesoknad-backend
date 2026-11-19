package no.nav.helse.flex.outbox

import no.nav.helse.flex.LogMarker
import no.nav.helse.flex.logger
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.withLock

@Component
class OutboxJob(
    private val outboxService: OutboxService,
) {
    private val log = logger()

    private val shutdown = AtomicBoolean()
    private val lock = ReentrantReadWriteLock()

    @EventListener
    fun onShutdownEvent(event: ContextClosedEvent) {
        log.info("OutboxJob shutdown starter")
        shutdown.set(true)
        try {
            val ventePåStopp = lock.writeLock().tryLock(20, SECONDS)
            if (!ventePåStopp) {
                log.warn("OutboxJob shutdown fullførte ikke innenfor 20 sekunder")
            } else {
                log.info("OutboxJob shutdown avsluttet gracefully")
                lock.writeLock().unlock()
            }
        } catch (_: InterruptedException) {
            log.error("OutboxJob shutdown avbrutt av interrupt")
            Thread.currentThread().interrupt()
        }
    }

    @Scheduled(initialDelayString = "\${outbox.job.initial-delay-ms:20000}", fixedDelay = 100, timeUnit = TimeUnit.MILLISECONDS)
    fun kjørJobb() {
        lock.readLock().withLock {
            try {
                do {
                    val prosesserteNoe = outboxService.sendEldsteUsendte()
                } while (prosesserteNoe && !shutdown.get())
            } catch (e: Exception) {
                // Exception-meldinger fra Kafka og Jackson kan inneholde payload, så detaljene går bare til team-logs.
                log.error("Feil under kjøring av OutboxJob: ${e.javaClass.name}")
                log.error(LogMarker.TEAM_LOG, "Feil under kjøring av OutboxJob", e)
            }
        }
    }
}
