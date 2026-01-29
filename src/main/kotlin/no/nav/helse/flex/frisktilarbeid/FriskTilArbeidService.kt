package no.nav.helse.flex.frisktilarbeid

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.tilPostgresJson
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.osloZone
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

@Service
class FriskTilArbeidService(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
    private val friskTilArbeidSoknadService: FriskTilArbeidSoknadService,
    private val leaderElection: LeaderElection,
) {
    private val log = logger()

    private val tidspunktForOvertakelse = LocalDateTime.of(2025, 3, 10, 0, 0, 0).atZone(osloZone).toOffsetDateTime()

    @Scheduled(initialDelay = 5, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun schedulertStartBehandlingAvFriskTilArbeidVedtakStatus() {
        if (leaderElection.isLeader()) {
            val vedtak = friskTilArbeidRepository.findById("24147bb6-f48f-4bed-8f37-7f10d0f0d3e4")
            if (vedtak.isPresent) {
                val oppdatertVedtak =
                    vedtak.get().copy(
                        fom = LocalDate.of(2026, 2, 26),
                        tom = LocalDate.of(2026, 5, 6),
                    )
                friskTilArbeidRepository.save(oppdatertVedtak)
                log.info(
                    "Oppdaterte FriskTilArbeid-vedtak med id: ${oppdatertVedtak.id} fra fom: ${vedtak.get().fom} til " +
                        "fom: ${oppdatertVedtak.fom} og tom: ${vedtak.get().tom} til tom: ${oppdatertVedtak.tom}.",
                )
            } else {
                log.info("Fant ikke FriskTilArbeid-vedtak med id: 24147bb6-f48f-4bed-8f37-7f10d0f0d3e4.")
            }
        }
    }

    fun lagreFriskTilArbeidVedtakStatus(kafkaMelding: FriskTilArbeidVedtakStatusKafkaMelding) {
        val friskTilArbeidVedtakStatus = kafkaMelding.friskTilArbeidVedtakStatus

        // Vi skal bare prosessere vedtak som er fattet etter 10. mars 2025 00:00 norsk tid.
        if (friskTilArbeidVedtakStatus.statusAt.isBefore(tidspunktForOvertakelse)) {
            return
        }

        if (friskTilArbeidVedtakStatus.status == Status.FATTET) {
            friskTilArbeidRepository
                .save(
                    FriskTilArbeidVedtakDbRecord(
                        vedtakUuid = UUID.randomUUID().toString(),
                        key = kafkaMelding.key,
                        opprettet = Instant.now(),
                        fnr = friskTilArbeidVedtakStatus.personident,
                        fom = friskTilArbeidVedtakStatus.fom,
                        tom = friskTilArbeidVedtakStatus.tom,
                        vedtak = friskTilArbeidVedtakStatus.tilPostgresJson(),
                        behandletStatus = BehandletStatus.NY,
                        ignorerArbeidssokerregister = kafkaMelding.ignorerArbeidssokerregister,
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
            friskTilArbeidRepository
                .finnVedtakSomSkalBehandles(antallVedtak)
                .also { if (it.isEmpty()) return emptyList() }

        log.info("Hentet ${dbRecords.size} FriskTilArbeidVedtakStatus for med status NY.")

        return dbRecords
            .map {
                friskTilArbeidSoknadService.opprettSoknader(it)
            }.flatten()
    }
}

fun String.tilFriskTilArbeidVedtakStatus(): FriskTilArbeidVedtakStatus = objectMapper.readValue(this)

data class FriskTilArbeidVedtakStatusKafkaMelding(
    val key: String,
    val friskTilArbeidVedtakStatus: FriskTilArbeidVedtakStatus,
    val ignorerArbeidssokerregister: Boolean? = null,
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
