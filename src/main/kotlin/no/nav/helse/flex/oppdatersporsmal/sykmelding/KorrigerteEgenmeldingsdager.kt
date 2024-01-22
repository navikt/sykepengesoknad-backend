package no.nav.helse.flex.oppdatersporsmal.sykmelding

import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.EttersendingSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.service.MottakerAvSoknadService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(rollbackFor = [Throwable::class])
class KorrigerteEgenmeldingsdager(
    private val identService: IdentService,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val ettersendingSoknadService: EttersendingSoknadService,
) {
    val log = logger()

    fun ettersendSoknaderTilNav(sykmeldingKafkaMessage: SykmeldingKafkaMessage) {
        try {
            val sykmeldingId = sykmeldingKafkaMessage.sykmelding.id
            val fnr = sykmeldingKafkaMessage.kafkaMetadata.fnr
            val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr)

            log.info("Sykmelding $sykmeldingId har fått oppdaterte svar, sjekker om søknader skal ettersendes til NAV")

            val sendteSoknader =
                sykepengesoknadDAO
                    .finnSykepengesoknader(identer)
                    .filter { it.status == Soknadstatus.SENDT }
                    .filter { it.fom != null }
                    .sortedBy { it.fom }

            val forsteSoknadForSykmeldingenBleBeregnetTilInnenforArbeidsgiverperioden =
                sendteSoknader.firstOrNull {
                    it.sykmeldingId == sykmeldingId && it.beregnetTilKunInnenforArbeidsgiverperioden()
                }
            if (forsteSoknadForSykmeldingenBleBeregnetTilInnenforArbeidsgiverperioden == null) {
                log.info("Søknader for sykmelding $sykmeldingId skal ikke ettersendes til NAV")
                return
            }

            sendteSoknader
                .filter {
                    it.arbeidsgiverOrgnummer ==
                        forsteSoknadForSykmeldingenBleBeregnetTilInnenforArbeidsgiverperioden.arbeidsgiverOrgnummer
                }
                .filter { it.beregnetTilKunInnenforArbeidsgiverperioden() }
                .filter {
                    mottakerAvSoknadService.finnMottakerAvSoknad(
                        it,
                        identer,
                        sykmeldingKafkaMessage,
                    ) != Mottaker.ARBEIDSGIVER
                }
                .forEach {
                    log.info("Ettersender søknad ${it.id} til NAV")
                    ettersendingSoknadService.ettersendTilNav(it)
                }
        } catch (e: Exception) {
            log.error("Feil ved ettersending av søknader til NAV", e)
            throw e
        }
    }

    private fun Sykepengesoknad.beregnetTilKunInnenforArbeidsgiverperioden(): Boolean {
        return sendtNav == null || (sendtArbeidsgiver != null && sendtNav.isAfter(sendtArbeidsgiver))
    }
}
