package no.nav.helse.flex.frisktilarbeid

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.objectMapper
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class FriskTilArbeidService(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    private val log = logger()

    fun lagreFriskTilArbeidVedtakStatus(friskTilArbeidVedtakStatusMelding: FriskTilArbeidVedtakStatusMelding) {
        val friskTilArbeidVedtakStatus = friskTilArbeidVedtakStatusMelding.friskTilArbeidVedtakStatus

        if (friskTilArbeidVedtakStatus.status == Status.FATTET) {
            friskTilArbeidRepository.save(
                FriskTilArbeidDbRecord(
                    timestamp = Instant.now(),
                    fnr = friskTilArbeidVedtakStatus.personident,
                    fom = friskTilArbeidVedtakStatus.fom,
                    tom = friskTilArbeidVedtakStatus.tom,
                    begrunnelse = friskTilArbeidVedtakStatus.begrunnelse,
                    vedtakStatus = friskTilArbeidVedtakStatus.tilPostgresJson(),
                    status = BehandletStatus.NY,
                ),
            )
        }

        log.info("Lagret FriskTilArbeidVedtakStatus med key: ${friskTilArbeidVedtakStatusMelding.key}.")
    }

    fun behandleFriskTilArbeidVedtakStatus(antallSomSkalBehandles: Int) {
        log.info("Behandler $antallSomSkalBehandles FriskTilArbeidVedtakStatus.")
        val dbRecords = friskTilArbeidRepository.finnVedtakSomSkalBehandles(antallSomSkalBehandles)

        dbRecords.forEach {
            // TODO: Kall til @Transactional-metode som opprettet én og én FriskTilArbeid-søknad.
            friskTilArbeidRepository.save(it.copy(status = BehandletStatus.BEHANDLET))
            log.info("Behandlet FriskTilArbeidVedtakStatus med id: ${it.id}.")
        }
    }
}

fun String.tilFriskTilArbeidVedtakStatus(): FriskTilArbeidVedtakStatus = objectMapper.readValue(this)

data class FriskTilArbeidVedtakStatusMelding(
    val key: String,
    val friskTilArbeidVedtakStatus: FriskTilArbeidVedtakStatus,
)

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
