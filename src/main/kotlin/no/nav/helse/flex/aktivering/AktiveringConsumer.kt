package no.nav.helse.flex.aktivering

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_AKTIVERING_TOPIC
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class AktiveringConsumer(
    private val soknadAktivering: SoknadAktivering,
    private val retryLogger: RetryLogger,
    private val environmentToggles: EnvironmentToggles,
) {
    val log = logger()

    @WithSpan
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
            soknadAktivering.aktiverSoknad(cr.key())
        } catch (e: Exception) {
            if (environmentToggles.isNotProduction()) {
                log.warn("feiler i dev, acker uansett", e)
                acknowledgment.acknowledge()
                return
            }
            val warnEllerErrorLogger = retryLogger.inkrementerRetriesOgReturnerLogger(cr.key())
            warnEllerErrorLogger.log(
                "Feilet ved aktivering av søknad ${cr.key()}.",
                e,
            )
        } finally {
            // Søknaden blir forsøkt aktivert igjen av AktiveringJobb.
            acknowledgment.acknowledge()
        }
    }
}
