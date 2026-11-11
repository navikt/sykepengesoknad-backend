package no.nav.helse.flex.forskuttering

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class NarmestelederEsyfoListener(
    private val oppdatering: OppdateringAvForskutteringEsyfo,
) {
    private val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [NARMESTELEDER_ESYFO_LEESAH_TOPIC],
        containerFactory = "esyfoKafkaListenerContainerFactory",
        autoStartup = "\${spring.kafka.listener.auto-startup:true}",
        concurrency = "1",
        id = "forskuttering-esyfo",
    )
    fun listen(
        records: List<ConsumerRecord<String, String?>>,
        acknowledgment: Acknowledgment,
    ) {
        if (records.isEmpty()) return
        try {
            oppdatering.behandleBatch(records)
            acknowledgment.acknowledge()
        } catch (_: Exception) {
            log.error("Feil i ESYFO-projeksjon ved behandling av batch")
            throw IllegalStateException("ESYFO-batch kan ikke behandles")
        }
    }
}
