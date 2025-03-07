package no.nav.helse.flex.frisktilarbeid

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.objectMapper
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

@Service
class FriskTilArbeidService(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
    private val friskTilArbeidSoknadService: FriskTilArbeidSoknadService,
) {
    private val log = logger()

    fun lagreFriskTilArbeidVedtakStatus(kafkaMelding: FriskTilArbeidVedtakStatusKafkaMelding) {
        val friskTilArbeidVedtakStatus = kafkaMelding.friskTilArbeidVedtakStatus

        if (friskTilArbeidVedtakStatus.status == Status.FATTET) {
            val eksisterendeVedtak = friskTilArbeidRepository.findByFnr(friskTilArbeidVedtakStatus.personident)

            eksisterendeVedtak.firstOrNull {
                friskTilArbeidVedtakStatus.tilPeriode().overlapper(it.tilPeriode())
            }?.apply {
                val feilmelding =
                    "Vedtak med key: ${kafkaMelding.key} og " +
                        "periode: [${friskTilArbeidVedtakStatus.fom} - ${friskTilArbeidVedtakStatus.tom}] " +
                        "overlapper med vedtak med periode: [$fom - $tom]."
                log.error(feilmelding)
                throw FriskTilArbeidVedtakStatusException(feilmelding)
            }

            friskTilArbeidRepository.save(
                FriskTilArbeidVedtakDbRecord(
                    vedtakUuid = UUID.randomUUID().toString(),
                    key = kafkaMelding.key,
                    opprettet = Instant.now(),
                    fnr = friskTilArbeidVedtakStatus.personident,
                    fom = friskTilArbeidVedtakStatus.fom,
                    tom = friskTilArbeidVedtakStatus.tom,
                    vedtak = friskTilArbeidVedtakStatus.tilPostgresJson(),
                    behandletStatus = BehandletStatus.NY,
                ),
            ).also {
                log.info(
                    "Lagret FriskTilArbeidVedtakStatus med id: ${it.id}, vedtak_uuid: ${it.vedtakUuid} og key: ${it.key}.",
                )
            }
        }
    }

    fun behandleFriskTilArbeidVedtakStatus(antallVedtak: Int): List<Sykepengesoknad> {
        val dbRecords =
            friskTilArbeidRepository.finnVedtakSomSkalBehandles(antallVedtak)
                .also { if (it.isEmpty()) return emptyList() }

        log.info("Hentet ${dbRecords.size} FriskTilArbeidVedtakStatus for med status NY.")

        return dbRecords.map {
            friskTilArbeidSoknadService.opprettSoknader(it)
        }.flatten()
    }
}

fun String.tilFriskTilArbeidVedtakStatus(): FriskTilArbeidVedtakStatus = objectMapper.readValue(this)

data class FriskTilArbeidVedtakStatusKafkaMelding(
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

fun FriskTilArbeidVedtakStatus.tilPeriode() = Periode(fom, tom)

enum class Status {
    FATTET,
    FERDIG_BEHANDLET,
}

class FriskTilArbeidVedtakStatusException(message: String) : RuntimeException(message)
