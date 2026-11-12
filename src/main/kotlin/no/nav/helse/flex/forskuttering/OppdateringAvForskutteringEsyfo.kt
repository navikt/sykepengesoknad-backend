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
    @Transactional(rollbackFor = [Exception::class])
    fun behandleBatch(records: List<ConsumerRecord<String, String?>>) {
        records.forEach { behandle(it) }
    }

    @Transactional(rollbackFor = [Exception::class])
    fun behandle(record: ConsumerRecord<String, String?>): Boolean {
        val sistBehandlet = checkpoint.lasOgHentOffset(record.topic(), record.partition())
        if (record.offset() <= sistBehandlet) return false

        val id = UUID.fromString(record.key())
        val verdi = record.value()
        if (verdi == null) {
            repository.deleteByNarmesteLederId(id)
        } else {
            val melding = objectMapper.readValue<NarmesteLederEsyfoLeesah>(verdi)
            require(melding.narmesteLederId == id) { "Nøkkel stemmer ikke med ESYFO-payload" }
            lagre(melding)
        }
        checkpoint.lagreOffset(record.topic(), record.partition(), record.offset())
        return true
    }

    private fun lagre(melding: NarmesteLederEsyfoLeesah) {
        val eksisterende = repository.findByNarmesteLederId(melding.narmesteLederId)
        repository.save(
            ForskutteringEsyfo(
                id = eksisterende?.id,
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
}
