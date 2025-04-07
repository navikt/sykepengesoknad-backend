package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerregisterClient
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
) {
    private val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun resetVedtaksPeriode() {
        if (leaderElection.isLeader()) {
            listOf(
                "f7ca2fd6-4fb2-4b17-bb88-a598e6a3f35d",
                "ca8852ab-a105-46a3-8f09-1ab51909dc89",
                "4e923bdb-f718-4f77-a1ea-ab494c7b8419",
                "421ea8bc-f741-45d4-a732-8fe62f139231",
                "336de282-cac7-44c0-b36c-713a05063f79",
                "334be4f1-51f8-461e-8c5b-61876ba9c9a6",
                "41b230dc-05c2-4576-a956-c914f98067df",
                "751edccc-15e3-47cc-a072-0b7ce61de31a",
                "87400fab-3107-4be0-b299-0a2be19aeaff",
                "505b5207-2695-4f1b-9c4f-3e8fdae8775b",
                "11d25de5-dfdc-4aef-81e5-a81c73ae22b4",
                "55df60f2-e9b8-499b-ab5e-9f9b228c4f49",
                "264a4119-1cb5-4bcd-8212-6aa60ed17650",
                "1f651669-1e55-47ec-99c8-2c2395ce3a33",
                "8643c203-5eb2-4a31-8f3a-eb688337dfed",
                "c8a63424-004a-420a-bd80-444db0511c55",
                "4cdc81fa-8539-41b3-8202-fec64477cb1d",
                "e4617632-4a7e-4001-9faf-321573d7d51c",
                "81efae28-c5bc-41e3-876c-2c9d13d9f422",
                "4bfa8ab0-09fa-4adc-9250-cf97e861e5c7",
                "73bf395a-4fbe-46cd-858e-d560ab85f914",
                "bee949dd-c637-443d-ae16-00b6f34f7b44",
                "e062aa96-e93b-47fa-9f0f-cc9265bd9ae2",
                "4180500f-82c8-40dc-85ab-0f9ec0b79e32",
                "44b0939f-9f37-45fc-87b7-cbec9f73adf7",
                "24325c19-9dbf-4b41-a02e-13207d01e301",
                "1bd1b7dd-da81-4853-ba29-f420cd6537f6",
                "0414f5f5-b47d-4496-8df9-1d992b7b5e95",
                "13791491-46b4-4ea5-90df-b2b63a4a783a",
                "9f26cee2-19ca-4676-a31c-a7452968a152",
            ).forEach {
                friskTilArbeidRepository.findById(it).get().copy(behandletStatus = BehandletStatus.NY).also {
                    friskTilArbeidRepository.save(it)
                }
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
            friskTilArbeidRepository.findByFnrIn(identer.alle()).filter { it.id != vedtakDbRecord.id }

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
