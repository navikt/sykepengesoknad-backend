package no.nav.helse.flex.forskuttering

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfo
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.module.kotlin.readValue
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
private data class NarmesteLederEsyfoLeesah(
    val narmesteLederId: UUID,
    val fnr: String,
    val orgnummer: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
    val timestamp: OffsetDateTime,
)

@Component
@Profile("!fakes")
class OppdateringAvForskutteringEsyfo(
    private val repository: ForskutteringEsyfoRepository,
    private val jdbcTemplate: JdbcTemplate,
) {
    @Transactional(rollbackFor = [Throwable::class])
    fun behandleBatch(records: List<ConsumerRecord<String, String?>>) {
        // Fast låserekkefølge mellom partisjoner; sorteringen bevarer record-rekkefølgen innen hver partisjon.
        records.sortedBy { it.partition() }.forEach { behandle(it) }
    }

    // Kalles synkront av én Kafka-listenertråd per tildelt partisjon. Låsen serialiserer
    // transaksjoner ved rebalance, men kan ikke sortere om records på ulike tråder.
    @Transactional(rollbackFor = [Throwable::class])
    fun behandle(record: ConsumerRecord<String, String?>): String {
        jdbcTemplate.update(
            """INSERT INTO forskuttering_esyfo_checkpoint (topic, partition)
               VALUES (?, ?) ON CONFLICT (topic, partition) DO NOTHING""",
            record.topic(),
            record.partition(),
        )
        val sistBehandlet =
            jdbcTemplate.queryForObject(
                """SELECT last_processed_offset FROM forskuttering_esyfo_checkpoint
                   WHERE topic = ? AND partition = ? FOR UPDATE""",
                Long::class.java,
                record.topic(),
                record.partition(),
            ) ?: error("Mangler checkpoint etter opprettelse")
        if (record.offset() <= sistBehandlet) {
            return "duplikat"
        }

        val key =
            try {
                val tekst = record.key() ?: throw IllegalArgumentException("Mangler key i ESYFO-record")
                require(UUID_FORMAT.matches(tekst))
                UUID.fromString(tekst)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Ugyldig UUID-key i ESYFO-record")
            }
        if (record.value() == null) {
            repository.slett(key)
        } else {
            val melding = objectMapper.readValue<NarmesteLederEsyfoLeesah>(record.value()!!)
            require(melding.narmesteLederId == key) { "Key og nærmeste leder-ID er ulike" }
            repository.lagre(
                ForskutteringEsyfo(
                    oppdatert = Instant.now(),
                    timestamp = melding.timestamp.toInstant(),
                    narmesteLederId = melding.narmesteLederId,
                    orgnummer = melding.orgnummer,
                    brukerFnr = melding.fnr,
                    aktivFom = melding.aktivFom,
                    aktivTom = melding.aktivTom,
                    arbeidsgiverForskutterer = melding.arbeidsgiverForskutterer,
                ),
            )
        }
        jdbcTemplate.update(
            """UPDATE forskuttering_esyfo_checkpoint SET last_processed_offset = ?
               WHERE topic = ? AND partition = ?""",
            record.offset(),
            record.topic(),
            record.partition(),
        )
        return if (record.value() == null) "slettet" else "oppdatert"
    }

    companion object {
        private val UUID_FORMAT = Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    }
}
