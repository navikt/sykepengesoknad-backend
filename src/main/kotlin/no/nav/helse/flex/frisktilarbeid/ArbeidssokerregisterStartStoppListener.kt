package no.nav.helse.flex.frisktilarbeid

import com.fasterxml.jackson.module.kotlin.readValue
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_START_STOPP_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class ArbeidssokerregisterStartStoppListener(
    val arbeidssokerregisterStartStoppService: ArbeidssokerregisterStartStoppService,
) {
    val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [ARBEIDSSOKERREGISTER_START_STOPP_TOPIC],
        id = "arbeidssokerregister-start-stopp-v1",
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val startStoppMelding = cr.value().tilArbeidssokerperiodeStartStoppMelding()

        log.info("Mottok ArbeidssokerregisterStartStoppMelding for vedtaksperiodeId: ${startStoppMelding.vedtaksperiodeId}.")
        arbeidssokerregisterStartStoppService.prosseserStartStoppMelding(startStoppMelding)
        acknowledgment.acknowledge()
    }
}

internal fun String.tilArbeidssokerperiodeStartStoppMelding(): ArbeidssokerperiodeStartStoppMelding = objectMapper.readValue(this)

data class ArbeidssokerperiodeStartStoppMelding(
    val operation: StartStopp,
    val vedtaksperiodeId: String,
    val fnr: String,
    val tidspunkt: Instant,
)

enum class StartStopp {
    START,
    STOPP
}
