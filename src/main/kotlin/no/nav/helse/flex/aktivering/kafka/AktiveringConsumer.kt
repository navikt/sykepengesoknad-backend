package no.nav.helse.flex.aktivering.kafka

import no.nav.helse.flex.aktivering.AktiverEnkeltSoknad
import no.nav.helse.flex.kafka.sykepengesoknadAktiveringTopic
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class AktiveringConsumer(
    private val aktiverEnkeltSoknad: AktiverEnkeltSoknad
) {

    @KafkaListener(
        topics = [sykepengesoknadAktiveringTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "sykepengesoknad-aktivering",
        idIsGroup = false,
        concurrency = "4"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {

        aktiverEnkeltSoknad.aktiverSoknad(cr.key())
        acknowledgment.acknowledge()
    }
}
