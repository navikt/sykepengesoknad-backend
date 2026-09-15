package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
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
        //TODO Duplicate handling
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(startStoppMelding.fnr)

        val alleFtaSoknaderSammeVedtaksid =
            hentSoknadService
                .hentSoknader(identer)
                .filter { it.soknadstype == Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING }
                .filter { it.friskTilArbeidVedtakId == startStoppMelding.vedtaksperiodeId }

        val soknaderSomSkalSlettes =
            alleFtaSoknaderSammeVedtaksid
                .filter { it.status == Soknadstatus.FREMTIDIG || it.status == Soknadstatus.NY }
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
            val soknadSomSlettes = it.copy(status = Soknadstatus.SLETTET)
            sykepengesoknadDAO.slettSoknad(soknadSomSlettes)
            soknadProducer.soknadEvent(soknadSomSlettes, null, false)
            log.info(
                "Slettet søknad: ${it.id} på grunn av FriskTilArbeidStoppMelding med avsluttetTidspunkt:" +
                    " ${startStoppMelding.tidspunkt} for vedtaksperiode: ${startStoppMelding.vedtaksperiodeId}.",
            )
        }
    }
}
