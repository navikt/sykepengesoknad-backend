package no.nav.helse.flex.frisktilarbeid

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@Profile("frisktilarbeid")
@Component
class FriskTilArbeidConsumer(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    val log = logger()

    @KafkaListener(
        topics = [FRISKTILARBEID_TOPIC],
        id = "flex-frisktilarbeid-dev-1",
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        log.info("Mottok FriskTilArbeidVedtakStatus med key: ${cr.key()}.")
        // TODO: Gjør mapping og annen logikk i Service-klasse.
        val vedtakStatusRecord = cr.value().tilFriskTilArbeidVedtakStatus()

        if (vedtakStatusRecord.status == Status.FATTET) {
            friskTilArbeidRepository.save(
                FriskTilArbeidDbRecord(
                    timestamp = Instant.now(),
                    fnr = vedtakStatusRecord.personident,
                    fom = vedtakStatusRecord.fom,
                    tom = vedtakStatusRecord.tom,
                    begrunnelse = vedtakStatusRecord.begrunnelse,
                    vedtakStatus = vedtakStatusRecord.tilPostgresJson(),
                ),
            )
            log.info("Lagret FriskTilArbeidVedtakStatus med key: ${cr.key()}.")
        }
        acknowledgment.acknowledge()
    }
}

fun String.tilFriskTilArbeidVedtakStatus(): FriskTilArbeidVedtakStatus = objectMapper.readValue(this)

data class FriskTilArbeidVedtakStatus(
    val uuid: String,
    val personident: String,
    val begrunnelse: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val status: Status,
    val statusAt: OffsetDateTime,
    val statusBy: String,
)

enum class Status {
    FATTET,
    FERDIG_BEHANDLET,
}
