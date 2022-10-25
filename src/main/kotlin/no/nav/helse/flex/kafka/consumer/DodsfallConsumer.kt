package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.logger
import no.nav.helse.flex.personhendelse.AivenDodsfallConsumer
import no.nav.helse.flex.personhendelse.Personhendelse
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class DodsfallConsumer(
    private val aivenDodsfallConsumer: AivenDodsfallConsumer
) {

    val log = logger()

    @KafkaListener(
        topics = ["aapen-person-pdl-leesah-v1"],
        id = "sykepengesoknad-personhendelse",
        idIsGroup = true,
        containerFactory = "kafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(cr: ConsumerRecord<String, GenericRecord>, acknowledgment: Acknowledgment) {
        aivenDodsfallConsumer.prosesserPersonhendelse(
            cr.value() as Personhendelse,
            cr.timestamp(),
        )
        acknowledgment.acknowledge()
    }
}
