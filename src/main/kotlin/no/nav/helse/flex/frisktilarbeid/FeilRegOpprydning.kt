package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.HentSoknadService
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class FeilRegOpprydning(
    private val leaderElection: LeaderElection,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
    private val hentSoknadService: HentSoknadService,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun resetVedtaksPeriode() {
        if (leaderElection.isLeader()) {
            log.info("Er leder, starter opprydning av feilregistrert vedtaksperioder.")

            val ftaId = "1d2cc925-d24c-4e58-b2cf-7b9f32421ddd"

            val soknaderTilSletting =
                listOf(
                    "1cfe741d-b4a3-3d3d-a22f-f44df4702732",
                    "7fdf733f-0056-37bb-b351-926252c40ea6",
                    "8eab246d-adcd-3d35-bcc7-a6417b8d9cd8",
                    "ca48e982-575d-359e-907a-bde465c10b98",
                    "97e1f836-70d7-36ef-94fe-613d74703cb9",
                    "e9832604-7690-307d-96d0-9e47c5fa7dc9",
                )

            soknaderTilSletting.forEach {
                try {
                    val soknaden = hentSoknadService.finnSykepengesoknad(it)
                    if (soknaden.status == Soknadstatus.SENDT) {
                        throw RuntimeException("Søknad med id: $it har status SENDT, kan ikke slettes.")
                    }
                    if (soknaden.friskTilArbeidVedtakId != ftaId) {
                        throw RuntimeException("Søknad med id: $it har ikke tilknyttet vedtaksperiodeId: $ftaId, kan ikke slettes.")
                    }
                    val soknadSomSlettes = soknaden.copy(status = Soknadstatus.SLETTET)
                    sykepengesoknadDAO.slettSoknad(soknadSomSlettes)
                    soknadProducer.soknadEvent(soknadSomSlettes, null, false)
                    log.info(
                        "Slettet søknad: $it i cronjobb på grunn av feilregistrering." +
                            " VedtaksperiodeId: ${soknadSomSlettes.friskTilArbeidVedtakId} med status: ${soknaden.status}.",
                    )
                } catch (e: Exception) {
                    log.error("Feil ved sletting av søknad med id: $it", e)
                    throw e
                }
            }
            friskTilArbeidRepository.findById(ftaId).get().also { vedtak ->
                val forventetFom = LocalDate.of(2025, 4, 3)
                if (vedtak.fom != forventetFom) {
                    throw RuntimeException(
                        "VedtaksperiodeId: $ftaId har feil fom-dato: ${vedtak.fom}, forventet: $forventetFom",
                    )
                }

                if (vedtak.id != ftaId) {
                    throw RuntimeException(
                        "VedtaksperiodeId: $ftaId har feil id: ${vedtak.id}, forventet: $ftaId",
                    )
                }

                // Sletter vedtaket og log info om det
                friskTilArbeidRepository.delete(vedtak)
                log.info(
                    "Slettet vedtak: $ftaId i cronjobb på grunn av feilregistrering." +
                        " VedtaksperiodeId: ${vedtak.id} med status: ${vedtak.behandletStatus}.",
                )
            }
        }
    }
}
