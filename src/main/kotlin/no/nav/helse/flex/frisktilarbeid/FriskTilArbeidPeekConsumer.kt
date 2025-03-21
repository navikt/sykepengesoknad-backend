package no.nav.helse.flex.frisktilarbeid

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.osloZone
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class FriskTilArbeidPeekConsumer(
    private val friskTilArbeidPeekRepository: FriskTilArbeidPeekRepository,
    private val environmentToggles: EnvironmentToggles,
) {
    val log = logger()

    private val tidspunktForOvertakelse = LocalDateTime.of(2025, 3, 10, 0, 0, 0).atZone(osloZone).toOffsetDateTime()

    @WithSpan
    @KafkaListener(
        topics = [FRISKTILARBEID_TOPIC],
        id = "flex-frisktilarbeid-peek-v1",
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        try {
            val friskTilArbeidVedtakStatus = cr.value().tilFriskTilArbeidVedtakStatus()
            if (friskTilArbeidVedtakStatus.statusAt.isAfter(tidspunktForOvertakelse)) {
                friskTilArbeidPeekRepository.save(
                    FriskTilArbeidPeekDbRecord(
                        fnr = friskTilArbeidVedtakStatus.personident,
                        fom = friskTilArbeidVedtakStatus.fom,
                        tom = friskTilArbeidVedtakStatus.tom,
                        vedtak = friskTilArbeidVedtakStatus.tilPostgresJson(),
                    ),
                ).also {
                    log.info("Lagret FriskTilArbeidPeekDbRecord med id: ${it.id}.")
                }
            }
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
