package no.nav.helse.flex.frisktilarbeid

import com.fasterxml.jackson.module.kotlin.readValue
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_STOPP_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Profile("frisktilarbeid")
@Component
class ArbeidssokerregisterStoppListener(
    val arbeidssokerregisterStoppService: ArbeidssokerregisterStoppService,
) {
    val log = logger()

    @WithSpan
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
        val stoppMelding = cr.value().tilArbeidssokerperiodeStoppMelding()

        log.info("Mottok ArbeidssokerregisterStoppMelding for vedtaksperiodeId: ${stoppMelding.vedtaksperiodeId}.")
        arbeidssokerregisterStoppService.prosseserStoppMelding(stoppMelding)
        acknowledgment.acknowledge()
    }
}

internal fun String.tilArbeidssokerperiodeStoppMelding(): ArbeidssokerperiodeStoppMelding = objectMapper.readValue(this)

data class ArbeidssokerperiodeStoppMelding(
    val vedtaksperiodeId: String,
    val fnr: String,
    val avsluttetTidspunkt: Instant,
)
