package no.nav.helse.flex.forskuttering

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class NarmestelederListener(
    private val oppdateringAvForskuttering: OppdateringAvForskuttering
) {

    @KafkaListener(
        topics = [NARMESTELEDER_LEESAH_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "narmesteleder-listener",
        idIsGroup = false,
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        oppdateringAvForskuttering.behandleMeldingFraKafka(cr.value())
        acknowledgment.acknowledge()
    }
}

const val NARMESTELEDER_LEESAH_TOPIC = "teamsykmelding.syfo-narmesteleder-leesah"
