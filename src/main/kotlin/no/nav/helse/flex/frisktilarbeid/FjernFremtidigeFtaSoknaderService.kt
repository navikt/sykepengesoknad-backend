package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
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
class FjernFremtidigeFtaSoknaderService(
    private val identService: IdentService,
    private val hentSoknadService: HentSoknadService,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    private val log = logger()

    @Transactional
    fun fjernFremtidigeFriskmeldtSoknaderHvisFerdig(soknad: Sykepengesoknad) {
        if (soknad.soknadstype != Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
            return
        }
        if (soknad.status != Soknadstatus.SENDT) {
            return
        }

        val svartNeiFortsattFriskmeldt = soknad.fortsattFriskmeldtTilArbeidsformidling() == false

        if (svartNeiFortsattFriskmeldt) {
            val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(soknad.fnr)

            val friskTilArbeidVedtakId = soknad.friskTilArbeidVedtakId ?: return
            val soknaderSomSkalSlettes =
                hentSoknadService.hentSoknader(identer)
                    .filter { it.soknadstype == Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING }
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
