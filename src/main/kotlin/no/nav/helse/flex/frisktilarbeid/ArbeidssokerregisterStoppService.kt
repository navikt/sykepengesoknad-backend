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
class ArbeidssokerregisterStoppService(
    private val identService: IdentService,
    private val hentSoknadService: HentSoknadService,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    private val log = logger()

    @Transactional
    fun prosseserStoppMelding(stoppMelding: ArbeidssokerperiodeStoppMelding) {
        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(stoppMelding.fnr)

        val alleFtaSoknaderSammeVedtaksid =
            hentSoknadService.hentSoknader(identer)
                .filter { it.soknadstype == Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING }
                .filter { it.friskTilArbeidVedtakId == stoppMelding.vedtaksperiodeId }

        val soknaderSomSkalSlettes =
            alleFtaSoknaderSammeVedtaksid
                .filter { it.status == Soknadstatus.FREMTIDIG || it.status == Soknadstatus.NY }
                .filter {
                    // Stoppmeldingen må komme fra et eksternt system. Må tillatte at nåværende periode kan sendes inn
                    it.fom!!.isAfter(stoppMelding.avsluttetTidspunkt.tilLocalDate())
                }

        friskTilArbeidRepository.findById(stoppMelding.vedtaksperiodeId).getOrNull()?.let {
            if (it.avsluttetTidspunkt == null) {
                friskTilArbeidRepository.save(it.copy(avsluttetTidspunkt = stoppMelding.avsluttetTidspunkt))
            }
        }

        soknaderSomSkalSlettes.forEach {
            val soknadSomSlettes = it.copy(status = Soknadstatus.SLETTET)
            sykepengesoknadDAO.slettSoknad(soknadSomSlettes)
            soknadProducer.soknadEvent(soknadSomSlettes, null, false)
            log.info(
                "Slettet søknad: ${it.id} på grunn av FriskTilArbeidStoppMelding med avsluttetTidspunkt:" +
                    " ${stoppMelding.avsluttetTidspunkt} for vedtaksperiode: ${stoppMelding.vedtaksperiodeId}.",
            )
        }
    }
}
