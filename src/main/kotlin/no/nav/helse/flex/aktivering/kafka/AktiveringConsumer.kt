package no.nav.helse.flex.aktivering.kafka

import no.nav.helse.flex.aktivering.AktiverEnkeltSoknad
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_AKTIVERING_TOPIC
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class AktiveringConsumer(
    private val aktiverEnkeltSoknad: AktiverEnkeltSoknad,
) {
    private val log = logger()

    @KafkaListener(
        topics = [SYKEPENGESOKNAD_AKTIVERING_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "sykepengesoknad-aktivering",
        idIsGroup = false,
        concurrency = "4",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            aktiverEnkeltSoknad.aktiverSoknad(cr.key())
        } catch (e: Exception) {
            // De som feiler blir lagt tilbake igjen av AktiveringJob.
            log.error(
                "Feilet ved aktivering av søknad ${cr.key()}, men blir plukket opp igjen av AktiveringJob som kjører om 2 timer.",
                e,
            )
        }

        acknowledgment.acknowledge()
    }
}
