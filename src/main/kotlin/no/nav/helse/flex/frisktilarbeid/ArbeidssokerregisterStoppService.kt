package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
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
        val alleSoknader = hentSoknadService.hentSoknader(identer)

        val fremtidigeFriskmeldtMedSammeVedtaksid =
            alleSoknader
                .filter {
                    it.soknadstype == Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING &&
                        it.friskTilArbeidVedtakId == stoppMelding.vedtaksperiodeId &&
                        it.status == Soknadstatus.FREMTIDIG
                }

        friskTilArbeidRepository.findById(stoppMelding.vedtaksperiodeId).getOrNull()?.let {
            friskTilArbeidRepository.save(it.copy(avsluttetTidspunkt = stoppMelding.avsluttetTidspunkt))
        }

        fremtidigeFriskmeldtMedSammeVedtaksid.forEach {
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
