package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerregisterClient
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.util.isBeforeOrEqual
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.util.*
import java.util.concurrent.TimeUnit

const val SOKNAD_PERIODELENGDE = 14L

@Service
class FriskTilArbeidSoknadService(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val identService: IdentService,
    private val arbeidssokerregisterClient: ArbeidssokerregisterClient,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun slettVedtak() {
        friskTilArbeidRepository.deleteById("2324c2f5-fa8e-447f-802e-41453114aa6b")
        log.info("Slettet FriskTilArbeid vedtak med id: 2324c2f5-fa8e-447f-802e-41453114aa6b")

        sykepengesoknadDAO.finnSykepengesoknad("3491cbd6-95eb-357d-bfdd-8ca292580d00").also {
            slettSoknadHvisStatusErNy(it)
        }

        sykepengesoknadDAO.finnSykepengesoknad("92e79c7d-391e-386a-9c96-05af9e9bbb6a").also {
            slettSoknadHvisStatusErNy(it)
        }

        sykepengesoknadDAO.finnSykepengesoknad("788c8c3e-3e3b-31ad-9160-7e0fd26cbadf").also {
            slettSoknadHvisStatusErNy(it)
        }
        sykepengesoknadDAO.finnSykepengesoknad("3bb0523e-c4a0-373d-b596-95d2dbeffa8d").also {
            slettSoknadHvisStatusErNy(it)
        }
        sykepengesoknadDAO.finnSykepengesoknad("500f80b2-00a7-346f-b594-a249fa679646").also {
            slettSoknadHvisStatusErNy(it)
        }

        friskTilArbeidRepository
            .findById("931d1db7-753d-460e-9155-67fe1d75d92f")
            .get()
            .copy(fom = LocalDate.of(2025, 3, 25), ignorerArbeidssokerregister = false)
            .also { friskTilArbeidRepository.save(it) }
        log.info("Oppdatert vedtak med id: 931d1db7-753d-460e-9155-67fe1d75d92f til tom dato: 2025-03-25.")
    }

    private fun slettSoknadHvisStatusErNy(sykepengesoknad: Sykepengesoknad) {
        if (sykepengesoknad.status == Soknadstatus.NY) {
            sykepengesoknadDAO.slettSoknad(sykepengesoknad.id)
            log.info("Sletter søknad med id: ${sykepengesoknad.id}.")
        } else {
            log.info("Slettet ikke søknad med id ${sykepengesoknad.id} da den har status: ${sykepengesoknad.status}.")
        }
    }

    @Transactional
    fun opprettSoknader(
        vedtakDbRecord: FriskTilArbeidVedtakDbRecord,
        periodeGenerator: (LocalDate, LocalDate, Long) -> List<Pair<LocalDate, LocalDate>> = ::defaultPeriodeGenerator,
    ): List<Sykepengesoknad> {
        val identer =
            identService.hentFolkeregisterIdenterMedHistorikkForFnr(vedtakDbRecord.fnr)
        val eksisterendeAndreVedtak =
            friskTilArbeidRepository
                .findByFnrIn(identer.alle())
                .filter { it.id != vedtakDbRecord.id }
                .filter { it.behandletStatus != BehandletStatus.OVERLAPP_OK }

        eksisterendeAndreVedtak
            .firstOrNull {
                vedtakDbRecord.tilPeriode().overlapper(it.tilPeriode())
            }?.apply {
                val feilmelding =
                    "Vedtak med key: ${vedtakDbRecord.key} og " +
                        "periode: [${vedtakDbRecord.fom} - ${vedtakDbRecord.tom}] " +
                        "overlapper med vedtak med key: $key periode: [$fom - $tom]."
                log.error(feilmelding)

                friskTilArbeidRepository.save(
                    vedtakDbRecord.copy(
                        behandletStatus = BehandletStatus.OVERLAPP,
                        behandletTidspunkt = Instant.now(),
                    ),
                )
                return emptyList()
            }

        if (vedtakDbRecord.sjekkArbeidssokerregisteret()) {
            val sisteArbeidssokerperiode =
                arbeidssokerregisterClient
                    .hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(vedtakDbRecord.fnr))
                    .singleOrNull()

            val status =
                when {
                    sisteArbeidssokerperiode == null -> BehandletStatus.INGEN_ARBEIDSSOKERPERIODE
                    sisteArbeidssokerperiode.avsluttet != null -> BehandletStatus.SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET
                    else -> null
                }

            if (status != null) {
                friskTilArbeidRepository.save(
                    vedtakDbRecord.copy(
                        behandletStatus = status,
                        behandletTidspunkt = Instant.now(),
                    ),
                )
                return emptyList()
            }
        }

        val eksisterendeSoknader =
            sykepengesoknadDAO.finnSykepengesoknader(
                listOf(vedtakDbRecord.fnr),
                Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
            )

        val soknader =
            periodeGenerator(vedtakDbRecord.fom, vedtakDbRecord.tom, SOKNAD_PERIODELENGDE).map { (fom, tom) ->
                lagSoknad(vedtakDbRecord, Periode(fom, tom))
            }

        val lagredeSoknader =
            soknader
                .filterNot { soknad ->
                    eksisterendeSoknader
                        .any {
                            soknad.fom == it.fom && soknad.tom == it.tom
                        }.also {
                            if (it) {
                                log.info(
                                    "Søknad ${soknad.id} med fom: ${soknad.fom} og tom: ${soknad.tom} for " +
                                        "friskTilArbeidVedtakId: ${soknad.friskTilArbeidVedtakId} eksisterer allerede.",
                                )
                            }
                        }
                }.map { soknad ->
                    val lagretSoknad = sykepengesoknadDAO.lagreSykepengesoknad(soknad)
                    soknadProducer.soknadEvent(lagretSoknad)
                    log.info("Opprettet søknad: ${soknad.id} for friskTilArbeidVedtakId: ${vedtakDbRecord.id}.")
                    lagretSoknad
                }

        friskTilArbeidRepository.save(
            vedtakDbRecord.copy(
                behandletStatus = BehandletStatus.BEHANDLET,
                behandletTidspunkt = Instant.now(),
            ),
        )
        return lagredeSoknader
    }

    private fun lagSoknad(
        vedtakDbRecord: FriskTilArbeidVedtakDbRecord,
        soknadsperiode: Periode,
    ): Sykepengesoknad {
        val grunnlagForId =
            "${vedtakDbRecord.id}${soknadsperiode.fom}${soknadsperiode.tom}${vedtakDbRecord.opprettet}"
        val soknadId = UUID.nameUUIDFromBytes(grunnlagForId.toByteArray()).toString()

        val sykepengesoknad =
            Sykepengesoknad(
                id = soknadId,
                fnr = vedtakDbRecord.fnr,
                startSykeforlop = vedtakDbRecord.fom,
                fom = soknadsperiode.fom,
                tom = soknadsperiode.tom,
                soknadstype = Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING,
                status = Soknadstatus.FREMTIDIG,
                opprettet = Instant.now(),
                sporsmal = emptyList(),
                utenlandskSykmelding = false,
                friskTilArbeidVedtakId = vedtakDbRecord.id,
            )

        return sykepengesoknad
    }
}

fun defaultPeriodeGenerator(
    periodeStart: LocalDate,
    periodeSlutt: LocalDate,
    periodeLengde: Long = SOKNAD_PERIODELENGDE,
): List<Pair<LocalDate, LocalDate>> {
    if (periodeSlutt.isBefore(periodeStart)) {
        throw IllegalArgumentException("Til-dato kan ikke være før fra-dato.")
    }
    return generateSequence(periodeStart) {
        it.plusDays(periodeLengde).takeIf { it.isBeforeOrEqual(periodeSlutt) }
    }.map { fom -> fom to minOf(fom.plusDays(periodeLengde - 1), periodeSlutt) }
        .toList()
}
