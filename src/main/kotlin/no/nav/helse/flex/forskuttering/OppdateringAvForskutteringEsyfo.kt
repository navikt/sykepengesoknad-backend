package no.nav.helse.flex.forskuttering

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfo
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
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
class OppdateringAvForskutteringEsyfo(
    private val repository: ForskutteringEsyfoRepository,
    private val checkpoint: ForskutteringEsyfoCheckpoint,
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
        val sistBehandlet = checkpoint.lasOgHentOffset(record.topic(), record.partition())
        if (record.offset() <= sistBehandlet) {
            return "duplikat"
        }

        val key = UUID.fromString(record.key())
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
        checkpoint.lagreOffset(record.topic(), record.partition(), record.offset())
        return if (record.value() == null) "slettet" else "oppdatert"
    }
}
