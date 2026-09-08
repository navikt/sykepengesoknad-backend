package no.nav.helse.flex.vedtaksperiodebehandling

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class VedtaksperiodeBehandlingConsumer {
    val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [SIS_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        id = "sis-consumer",
        idIsGroup = false,
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("Holder offset up to date imens reprosessering kjører")

        acknowledgment.acknowledge()
    }
}
