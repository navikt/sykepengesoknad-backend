package no.nav.helse.flex.aktivering.kafka

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.aktivering.AktiverEnkeltSoknad
import no.nav.helse.flex.kafka.sykepengesoknadAktiveringTopic
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.OBJECT_MAPPER
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class AktiveringConsumer(
    private val aktiverEnkeltSoknad: AktiverEnkeltSoknad,
    private val aktiveringProducer: AktiveringProducer,
) {
    private val log = logger()

    @KafkaListener(
        topics = [sykepengesoknadAktiveringTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "sykepengesoknad-aktivering",
        idIsGroup = false,
        concurrency = "4"
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        try {
            aktiverEnkeltSoknad.aktiverSoknad(cr.key())
        } catch (e: Exception) {
            // Forsøker å aktivere denne senere
            log.warn("Feilet ved aktivering av søknad ${cr.key()}, legger den tilbake og forsøker igjen senere")
            val aktiveringBestilling: AktiveringBestilling = OBJECT_MAPPER.readValue(cr.value())
            aktiveringProducer.leggPaAktiveringTopic(aktiveringBestilling)
        }

        acknowledgment.acknowledge()
    }
}
