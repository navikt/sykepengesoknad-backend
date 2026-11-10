package no.nav.helse.flex.forskuttering

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
            oppdatering.behandle(cr)
            acknowledgment.acknowledge()
        } catch (_: Exception) {
            log.error("Feil i ESYFO-projeksjon, partition={}, offset={}", cr.partition(), cr.offset())
            throw IllegalStateException("ESYFO-record kan ikke behandles")
        }
    }
}
