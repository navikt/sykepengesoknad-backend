package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Soknadstatus.FREMTIDIG
import no.nav.helse.flex.domain.Soknadstatus.NY
import no.nav.helse.flex.domain.Soknadstatus.SENDT
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
        val vedtak =
            checkNotNull(friskTilArbeidRepository.findById(startStoppMelding.vedtaksperiodeId).getOrNull()) {
                "Fant ikke vedtak for ${startStoppMelding.operation}-melding med " +
                    "vedtaksperiode: ${startStoppMelding.vedtaksperiodeId}."
            }

        check(vedtak.skalSjekkeArbeidssokerregisteret()) {
            "Kan ikke behandle ${startStoppMelding.operation}-melding for vedtaksperiode: ${vedtak.id} " +
                "siden vedtaket er satt til å ignorere arbeidssøkerregisteret."
        }

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(startStoppMelding.fnr)

        val alleFtaSoknaderSammeVedtaksid =
            hentSoknadService
                .hentSoknader(identer)
                .filter { it.soknadstype == FRISKMELDT_TIL_ARBEIDSFORMIDLING }
                .filter { it.friskTilArbeidVedtakId == vedtak.id }

        when (startStoppMelding.operation) {
            STOPP -> avsluttVedtaksperiode(vedtak, alleFtaSoknaderSammeVedtaksid, startStoppMelding)
            START -> gjenopprettVedtaksperiode(vedtak, alleFtaSoknaderSammeVedtaksid)
        }
    }

    private fun gjenopprettVedtaksperiode(
        vedtak: FriskTilArbeidVedtakDbRecord,
        alleFtaSoknaderSammeVedtaksid: List<Sykepengesoknad>,
    ) {
        checkNotNull(vedtak.avsluttetTidspunkt) {
            "Kan ikke gjenopprette vedtaksperiode: ${vedtak.id} når den aldri har blitt avsluttet."
        }
        check(vedtak.behandletStatus == BEHANDLET) {
            "Kan ikke gjenopprette vedtaksperiode: ${vedtak.id} som har status ${vedtak.behandletStatus}."
        }

        val harSvartIkkeFortsattFriskmeldt =
            alleFtaSoknaderSammeVedtaksid.any { soknad ->
                soknad.status == SENDT && soknad.fortsattFriskmeldtTilArbeidsformidling() == false
            }

        if (harSvartIkkeFortsattFriskmeldt) {
            log.info(
                "Bruker har svart at de ikke vil være registert som arbeidssøker. Gjenoppretter ikke vedtaksperiode: ${vedtak.id}.",
            )
            return
        }

        friskTilArbeidRepository.save(vedtak.copy(avsluttetTidspunkt = null, behandletStatus = BehandletStatus.NY))
        log.info(
            "Satt BehandletStatus til NY siden vi fikk melding om at det er opprettet en ny arbeidssøkerperiode " +
                "for vedtaksperiode: ${vedtak.id}",
        )
    }

    private fun avsluttVedtaksperiode(
        vedtak: FriskTilArbeidVedtakDbRecord,
        alleFtaSoknaderSammeVedtaksid: List<Sykepengesoknad>,
        startStoppMelding: ArbeidssokerperiodeStartStoppMelding,
    ) {
        val soknaderSomSkalSlettes =
            alleFtaSoknaderSammeVedtaksid
                .filter { it.status == FREMTIDIG || it.status == NY }
                .filter {
                    it.fom!!.isAfter(startStoppMelding.tidspunkt.tilLocalDate())
                }

        if (vedtak.avsluttetTidspunkt == null) {
            friskTilArbeidRepository.save(vedtak.copy(avsluttetTidspunkt = startStoppMelding.tidspunkt))
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
