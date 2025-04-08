package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerregisterClient
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.cronjob.LeaderElection
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
    private val leaderElection: LeaderElection,
    private val environmentToggles: EnvironmentToggles,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun resetVedtaksPeriode() {
        if (leaderElection.isLeader() && environmentToggles.isProduction()) {
            friskTilArbeidRepository.findById("8b3d31eb-22e6-4541-a916-8e0c07251e1f").get().also { vedtak ->
                val oppdatertVedtak =
                    vedtak.copy(
                        behandletStatus = BehandletStatus.NY,
                        tom = LocalDate.of(2025, 5, 28),
                    )
                friskTilArbeidRepository.save(oppdatertVedtak)
                log.info(
                    "Oppdatert vedtak: ${vedtak.id} med status: ${vedtak.behandletStatus} til status: ${oppdatertVedtak.behandletStatus}" +
                        " og fra tom: ${vedtak.tom} til tom: ${oppdatertVedtak.tom}",
                )
            }

            friskTilArbeidRepository.findById("8a8ad333-15a7-4860-a635-c3db950ff398").get().also { vedtak ->
                val oppdatertVedtak =
                    vedtak.copy(
                        behandletStatus = BehandletStatus.NY,
                        tom = LocalDate.of(2025, 6, 23),
                    )
                friskTilArbeidRepository.save(oppdatertVedtak)
                log.info(
                    "Oppdatert vedtak: ${vedtak.id} med status: ${vedtak.behandletStatus} til status: ${oppdatertVedtak.behandletStatus}" +
                        " og fra tom: ${vedtak.tom} til tom: ${oppdatertVedtak.tom}",
                )
            }
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
            friskTilArbeidRepository.findByFnrIn(identer.alle())
                .filter { it.id != vedtakDbRecord.id }
                .filter { it.behandletStatus != BehandletStatus.OVERLAPP_OK }

        eksisterendeAndreVedtak.firstOrNull {
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

        val sisteArbeidssokerperiode =
            arbeidssokerregisterClient.hentSisteArbeidssokerperiode(
                ArbeidssokerperiodeRequest(
                    vedtakDbRecord.fnr,
                ),
            ).singleOrNull()

        if (sisteArbeidssokerperiode == null) {
            friskTilArbeidRepository.save(
                vedtakDbRecord.copy(
                    behandletStatus = BehandletStatus.INGEN_ARBEIDSSOKERPERIODE,
                    behandletTidspunkt = Instant.now(),
                ),
            )
            return emptyList()
        }

        if (sisteArbeidssokerperiode.avsluttet != null) {
            friskTilArbeidRepository.save(
                vedtakDbRecord.copy(
                    behandletStatus = BehandletStatus.SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET,
                    behandletTidspunkt = Instant.now(),
                ),
            )
            return emptyList()
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
            soknader.filterNot { soknad ->
                eksisterendeSoknader.any {
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
    }
        .map { fom -> fom to minOf(fom.plusDays(periodeLengde - 1), periodeSlutt) }
        .toList()
}
