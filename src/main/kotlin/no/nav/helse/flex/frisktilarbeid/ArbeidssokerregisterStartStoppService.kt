package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.SLETTET
import no.nav.helse.flex.domain.Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.fortsattFriskmeldtTilArbeidsformidling
import no.nav.helse.flex.frisktilarbeid.BehandletStatus.BEHANDLET
import no.nav.helse.flex.frisktilarbeid.StartStopp.START
import no.nav.helse.flex.frisktilarbeid.StartStopp.STOPP
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.util.tilLocalDate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import kotlin.jvm.optionals.getOrNull

@Component
class ArbeidssokerregisterStartStoppService(
    private val identService: IdentService,
    private val hentSoknadService: HentSoknadService,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    private val log = logger()

    @Transactional
    fun prosseserStartStoppMelding(startStoppMelding: ArbeidssokerperiodeStartStoppMelding) {
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(startStoppMelding.fnr)

        val alleFtaSoknaderSammeVedtaksid =
            hentSoknadService
                .hentSoknader(identer)
                .filter { it.soknadstype == FRISKMELDT_TIL_ARBEIDSFORMIDLING }
                .filter { it.friskTilArbeidVedtakId == startStoppMelding.vedtaksperiodeId }

        when (startStoppMelding.operation) {
            STOPP -> stopp(alleFtaSoknaderSammeVedtaksid, startStoppMelding)
            START -> start(alleFtaSoknaderSammeVedtaksid, startStoppMelding)
        }
    }

    private fun start(
        alleFtaSoknaderSammeVedtaksid: List<Sykepengesoknad>,
        startStoppMelding: ArbeidssokerperiodeStartStoppMelding,
    ) {
        friskTilArbeidRepository.findById(startStoppMelding.vedtaksperiodeId).getOrNull()?.let {
            if (it.avsluttetTidspunkt == null) {
                throw IllegalStateException(
                    "Kan ikke gjenopprette søknad når den aldri har blitt avsluttet, " +
                        "avsluttetTidspunkt=${it.avsluttetTidspunkt}, vedtaksperiodeId=$it.vedtaksperiodeId",
                )
            }

            if (it.behandletStatus != BEHANDLET) {
                throw IllegalStateException(
                    "Kan ikke gjenopprette søknad som ikke er behandlet, " +
                        "behandletStatus=${it.behandletStatus}, vedtaksperiodeId=$it.vedtaksperiodeId",
                )
            }

            val nyesteSoknad =
                alleFtaSoknaderSammeVedtaksid
                    .maxByOrNull { soknad -> soknad.fom!! }

            if (nyesteSoknad?.fortsattFriskmeldtTilArbeidsformidling() == false) {
                log.info(
                    "Bruker har svart at de ikke fortsatt er friskmeldt til arbeidsformidling, " +
                        "vedtaksperiodeId=$it.vedtaksperiodeId",
                )
                return
            }

            friskTilArbeidRepository.save(it.copy(avsluttetTidspunkt = null, behandletStatus = BehandletStatus.NY))
            log.info(
                "Satt BehandletStatus til NY siden vi fikk melding om at det er opprettet en ny arbeidssøkerperiode " +
                    "for søknad: ${it.id}",
            )
        }
    }

    private fun stopp(
        alleFtaSoknaderSammeVedtaksid: List<Sykepengesoknad>,
        startStoppMelding: ArbeidssokerperiodeStartStoppMelding,
    ) {
        val soknaderSomSkalSlettes =
            alleFtaSoknaderSammeVedtaksid
                .filter { it.status == FREMTIDIG || it.status == NY }
                .filter {
                    // Stoppmeldingen må komme fra et eksternt system. Må tillatte at nåværende periode kan sendes inn
                    it.fom!!.isAfter(startStoppMelding.tidspunkt.tilLocalDate())
                }

        friskTilArbeidRepository.findById(startStoppMelding.vedtaksperiodeId).getOrNull()?.let {
            if (it.avsluttetTidspunkt == null) {
                friskTilArbeidRepository.save(it.copy(avsluttetTidspunkt = startStoppMelding.tidspunkt))
            }
        }

        soknaderSomSkalSlettes.forEach {
            val soknadSomSlettes = it.copy(status = SLETTET)
            sykepengesoknadDAO.slettSoknad(soknadSomSlettes)
            soknadProducer.soknadEvent(soknadSomSlettes, null, false)
            log.info(
                "Slettet søknad: ${it.id} på grunn av FriskTilArbeidStartStoppMelding med tidspunkt:" +
                    " ${startStoppMelding.tidspunkt} for vedtaksperiode: ${startStoppMelding.vedtaksperiodeId}.",
            )
        }
    }
}
