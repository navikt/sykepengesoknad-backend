package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.fortsattFriskmeldtTilArbeidsformidling
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import kotlin.jvm.optionals.getOrNull

@Component
class FriskTilArbeidFortsattFriskmeldtService(
    private val identService: IdentService,
    private val hentSoknadService: HentSoknadService,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
    private val friskTilArbeidSoknadService: FriskTilArbeidSoknadService
) {
    private val log = logger()

    @Transactional
    fun behandleSvarPaFortsattFriskmeldt(soknad: Sykepengesoknad) {
        if (soknad.soknadstype != FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
            return
        }
        if (soknad.status != Soknadstatus.SENDT) {
            return
        }

        val erFortsattFriskmeldt = soknad.fortsattFriskmeldtTilArbeidsformidling() ?: return // TODO Håndter null?

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)
        val friskTilArbeidVedtakId = soknad.friskTilArbeidVedtakId ?: return

        if (erFortsattFriskmeldt) {
            friskTilArbeidRepository.findById(friskTilArbeidVedtakId).getOrNull()?.let {
                // TODO Throw error on null?
                friskTilArbeidSoknadService.opprettSoknader(it)
                log.info(
                    "Opprettet nye fremtidige søknader på grunn av svar ja på fortsatt friskmeldt i søknad " +
                        " ${soknad.id} for friskTilArbeidVedtakId: $friskTilArbeidVedtakId.",
                )
            }

        } else {
            val soknaderSomSkalSlettes =
                hentSoknadService
                    .hentSoknader(identer)
                    .filter { it.soknadstype == FRISKMELDT_TIL_ARBEIDSFORMIDLING }
                    .filter { it.friskTilArbeidVedtakId == friskTilArbeidVedtakId }
                    .filter { it.status == Soknadstatus.FREMTIDIG || it.status == Soknadstatus.NY }
                    .filter { it.fom!!.isAfter(soknad.fom!!) }

            soknaderSomSkalSlettes.forEach {
                val soknadSomSlettes = it.copy(status = Soknadstatus.SLETTET)
                sykepengesoknadDAO.slettSoknad(soknadSomSlettes)
                soknadProducer.soknadEvent(soknadSomSlettes, null, false)
                log.info(
                    "Slettet søknad: ${it.id} på grunn av svar nei på fortsatt friskmeldt i søknad " +
                        " ${soknad.id} for friskTilArbeidVedtakId: $friskTilArbeidVedtakId.",
                )
            }

            friskTilArbeidRepository.findById(friskTilArbeidVedtakId).getOrNull()?.let {
                if (it.avsluttetTidspunkt == null) {
                    friskTilArbeidRepository.save(it.copy(avsluttetTidspunkt = Instant.now()))
                }
            }
        }
    }
}
