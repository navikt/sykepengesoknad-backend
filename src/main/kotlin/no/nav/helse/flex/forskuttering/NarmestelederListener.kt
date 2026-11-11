package no.nav.helse.flex.forskuttering

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class NarmestelederListener(
    private val oppdateringAvForskuttering: OppdateringAvForskuttering,
) {
    private val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [NARMESTELEDER_LEESAH_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "narmesteleder-listener",
        idIsGroup = false,
    )
    fun listen(
        cr: ConsumerRecord<String, String?>,
        acknowledgment: Acknowledgment,
    ) {
        val melding = cr.value()
        if (melding == null) {
            log.info("Ignorerer tombstone på gammelt topic")
        } else {
            oppdateringAvForskuttering.behandleMeldingFraKafka(melding)
        }
        acknowledgment.acknowledge()
    }
}

const val NARMESTELEDER_LEESAH_TOPIC = "teamsykmelding.syfo-narmesteleder-leesah"
const val NARMESTELEDER_ESYFO_LEESAH_TOPIC = "team-esyfo.syfo-narmesteleder-leesah"
