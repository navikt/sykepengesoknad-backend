package no.nav.helse.flex.frisktilarbeid

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class FriskTilArbeidConsumer(
    private val friskTilArbeidService: FriskTilArbeidService,
    private val environmentToggles: EnvironmentToggles,
) {
    val log = logger()

    @WithSpan
    @KafkaListener(
        topics = [FRISKTILARBEID_TOPIC],
        id = "flex-frisktilarbeid-v1",
        containerFactory = "aivenKafkaListenerContainerFactory",
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
                FriskTilArbeidVedtakStatusKafkaMelding(
                    cr.key(),
                    cr.value().tilFriskTilArbeidVedtakStatus(),
                ),
            )
            acknowledgment.acknowledge()
        } catch (e: Exception) {
            if (environmentToggles.isNotProduction()) {
                log.error(
                    "Feilet ved mottak av FriskTilArbeidVedtakStatus men Ack-er melding siden " +
                        "environment er ${environmentToggles.environment()}.",
                    e,
                )
                acknowledgment.acknowledge()
            } else {
                log.error("Feilet ved mottak av FriskTilArbeidVedtakStatus.", e)
                throw e
            }
        }
    }
}
