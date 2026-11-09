package no.nav.helse.flex.forskuttering

import io.micrometer.core.instrument.MeterRegistry
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("!fakes")
class NarmestelederEsyfoListener(
    private val oppdatering: OppdateringAvForskutteringEsyfo,
    private val checkpointMetrikk: ForskutteringEsyfoCheckpointMetrikk,
    private val meterRegistry: MeterRegistry,
) {
    private val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [NARMESTELEDER_ESYFO_LEESAH_TOPIC],
        containerFactory = "esyfoKafkaListenerContainerFactory",
        autoStartup = "\${FORSKUTTERING_ESYFO_CONSUMER_AKTIV:false}",
        concurrency = "1",
        id = "esyfo-narmesteleder-listener",
        idIsGroup = false,
    )
    fun listen(
        cr: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            val utfall = oppdatering.behandle(cr)
            checkpointMetrikk.registrer(cr.partition())
            meterRegistry.counter("forskuttering_esyfo_records_total", "utfall", utfall).increment()
            acknowledgment.acknowledge()
        } catch (_: Exception) {
            meterRegistry.counter("forskuttering_esyfo_records_total", "utfall", "feil").increment()
            log.error("Feil i ESYFO-projeksjon, partition={}, offset={}", cr.partition(), cr.offset())
            throw IllegalStateException("ESYFO-record kan ikke behandles")
        }
    }
}
