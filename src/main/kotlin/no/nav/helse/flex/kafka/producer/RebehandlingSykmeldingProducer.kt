package no.nav.helse.flex.kafka.producer

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.kafka.SYKMELDING_SENDT_RETRY_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.kafka.SyfoProducerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

const val BEHANDLINGSTIDSPUNKT = "behandlingstidspunkt"

@Component
class RebehandlingSykmeldingSendtProducer(
    private val producer: KafkaProducer<String, String>,
) {
    val log = logger()

    @WithSpan
    fun leggPaRebehandlingTopic(
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
        behandlingstidspunkt: OffsetDateTime,
    ) {
        try {
            producer
                .send(
                    SyfoProducerRecord<String, String>(
                        SYKMELDING_SENDT_RETRY_TOPIC,
                        sykmeldingKafkaMessage.event.sykmeldingId,
                        sykmeldingKafkaMessage.serialisertTilString(),
                        mapOf(
                            Pair(
                                BEHANDLINGSTIDSPUNKT,
                                behandlingstidspunkt.toInstant().toEpochMilli(),
                            ),
                        ),
                    ),
                ).get()
        } catch (exception: Exception) {
            log.error("Det feiler når sykmelding ${sykmeldingKafkaMessage.sykmelding} skal legges på rebehandling-topic", exception)
            throw RuntimeException(exception)
        }
    }
}
