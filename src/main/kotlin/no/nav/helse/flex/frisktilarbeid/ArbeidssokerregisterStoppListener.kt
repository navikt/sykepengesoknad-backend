package no.nav.helse.flex.frisktilarbeid

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_STOPP_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Profile("frisktilarbeid")
@Component
class ArbeidssokerregisterStoppListener(
    val arbeidssokerregisterStoppService: ArbeidssokerregisterStoppService,
) {
    val log = logger()

    @KafkaListener(
        topics = [ARBEIDSSOKERREGISTER_STOPP_TOPIC],
        id = "arbeidssokerregister-stopp-dev-1",
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val stoppMelding = cr.value().tilArbeidssokerregisterStoppMelding()

        log.info("Mottok ArbeidssokerregisterStoppMelding med key: ${cr.key()} og id: ${stoppMelding.id}.")
        arbeidssokerregisterStoppService.prosseserStoppMelding(stoppMelding)
        acknowledgment.acknowledge()
    }
}

internal fun String.tilArbeidssokerregisterStoppMelding(): ArbeidssokerregisterStoppMelding = objectMapper.readValue(this)

data class ArbeidssokerregisterStoppMelding(
    val id: String,
    val fnr: String,
)
