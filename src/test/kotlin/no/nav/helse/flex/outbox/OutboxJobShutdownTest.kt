package no.nav.helse.flex.outbox

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.juridiskvurdering.JuridiskVurdering
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.junit.jupiter.api.Test
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.support.GenericApplicationContext
import java.time.OffsetDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

class OutboxJobShutdownTest {
    private fun shutdownEvent() = ContextClosedEvent(GenericApplicationContext())

    @Test
    fun `kjørJobb avslutter loopen når shutdown signaleres selv om det alltid er mer å prosessere`() {
        val service = KontrollerbarOutboxService(returverdi = true)
        val job = OutboxJob(service)

        val jobbTråd = thread { job.kjørJobb() }

        ventTil(sekunder = 5) { service.antallKall.get() >= 3 }

        job.onShutdownEvent(shutdownEvent())

        jobbTråd.join(5_000)
        jobbTråd.isAlive shouldBe false

        val kallVedShutdown = service.antallKall.get()
        Thread.sleep(200)
        service.antallKall.get() shouldBe kallVedShutdown
    }

    @Test
    fun `onShutdownEvent venter til pågående jobb er ferdig før den fullfører`() {
        val service = KontrollerbarOutboxService(returverdi = true, blokkerFørsteKall = true)
        val job = OutboxJob(service)

        thread { job.kjørJobb() }
        service.førsteKallStartet.await(5, TimeUnit.SECONDS) shouldBe true

        val shutdownFullført = CountDownLatch(1)
        val shutdownTråd =
            thread {
                job.onShutdownEvent(shutdownEvent())
                shutdownFullført.countDown()
            }

        shutdownFullført.await(500, TimeUnit.MILLISECONDS) shouldBe false

        service.slippFørsteKall.countDown()
        shutdownFullført.await(5, TimeUnit.SECONDS) shouldBe true
        shutdownTråd.join(1_000)
    }

    @Test
    fun `kjørJobb svelger feil slik at neste kjøring kan forsøke igjen`() {
        val service = KontrollerbarOutboxService(returverdi = true, kastFeil = true)
        val job = OutboxJob(service)

        job.kjørJobb()
        job.kjørJobb()

        service.antallKall.get() shouldBe 2
    }

    private fun ventTil(
        sekunder: Long,
        betingelse: () -> Boolean,
    ) {
        val frist = System.nanoTime() + TimeUnit.SECONDS.toNanos(sekunder)
        while (System.nanoTime() < frist) {
            if (betingelse()) return
            Thread.sleep(10)
        }
        throw AssertionError("Betingelsen ble ikke oppfylt innen $sekunder sekunder")
    }
}

private class KontrollerbarOutboxService(
    private val returverdi: Boolean,
    private val blokkerFørsteKall: Boolean = false,
    private val kastFeil: Boolean = false,
) : OutboxService {
    val antallKall = AtomicInteger(0)
    val førsteKallStartet = CountDownLatch(1)
    val slippFørsteKall = CountDownLatch(1)

    override fun sendUsendteForEldsteLedigeFnr(): Boolean {
        val kallnummer = antallKall.incrementAndGet()
        if (kastFeil) {
            throw RuntimeException("Kafka er nede")
        }
        if (blokkerFørsteKall && kallnummer == 1) {
            førsteKallStartet.countDown()
            slippFørsteKall.await(10, TimeUnit.SECONDS)
        }
        return returverdi
    }

    override fun outboxSykepengesoknad(sykepengesoknadDTO: SykepengesoknadDTO) = error("Ikke i bruk i denne testen")

    override fun outboxJuridiskVurdering(juridiskVurdering: JuridiskVurdering) = error("Ikke i bruk i denne testen")

    override fun outboxAktivering(aktiveringBestilling: AktiveringBestilling) = error("Ikke i bruk i denne testen")

    override fun outboxRebehandlingSykmelding(
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
        behandlingstidspunkt: OffsetDateTime,
    ) = error("Ikke i bruk i denne testen")

    override fun outboxAuditEntry(auditEntry: AuditEntry) = error("Ikke i bruk i denne testen")
}
