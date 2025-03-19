package no.nav.helse.flex.aktivering

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_AKTIVERING_TOPIC
import no.nav.helse.flex.logger
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class AktiveringProducer(private val aktiveringKafkaProducer: Producer<String, AktiveringBestilling>) {
    val log = logger()

    @WithSpan
    fun leggPaAktiveringTopic(aktiveringBestilling: AktiveringBestilling) {
        try {
            aktiveringKafkaProducer.send(
                ProducerRecord(
                    SYKEPENGESOKNAD_AKTIVERING_TOPIC,
                    aktiveringBestilling.soknadId,
                    aktiveringBestilling,
                ),
            ).get()
        } catch (exception: Exception) {
            log.error(
                "Det feiler når aktivering bestilling ${aktiveringBestilling.soknadId} skal legges på $SYKEPENGESOKNAD_AKTIVERING_TOPIC",
                exception,
            )
            throw RuntimeException(exception)
        }
    }
}
