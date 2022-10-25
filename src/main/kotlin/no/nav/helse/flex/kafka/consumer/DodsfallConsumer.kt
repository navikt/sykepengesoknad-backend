package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.logger
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class DodsfallConsumer(
    private val aivenDodsfallConsumer: AivenDodsfallConsumer
) : ConsumerSeekAware {

    val log = logger()

    @KafkaListener(
        topics = ["aapen-person-pdl-leesah-v1"],
        id = "sykepengesoknad-personhendelse",
        idIsGroup = true,
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(cr: ConsumerRecord<String, GenericRecord>, acknowledgment: Acknowledgment) {
        log.info("Mottok personhendelse på aapen-person-pdl-leesah-v1")

        aivenDodsfallConsumer.prosesserPersonhendelse(
            cr.value(),
            cr.timestamp(),
        )
        acknowledgment.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: MutableMap<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback
    ) {
        assignments.forEach {
            callback.seekRelative(it.key.topic(), it.key.partition(), -1, false)
        }
    }
}
