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

            val ftaId = "1a5cf7ab-9b6a-431f-82da-e2288998029a"

            friskTilArbeidRepository.findById(ftaId).get().also { vedtak ->
                val forventetTom = LocalDate.of(2025, 4, 25)
                if (vedtak.tom != forventetTom) {
                    vedtak.copy(
                        tom = forventetTom,
                    ).also {
                        friskTilArbeidRepository.save(it)
                        log.info(
                            "Oppdatert vedtak: ${vedtak.id} med status: ${vedtak.behandletStatus} til t.o.m: ${it.tom}",
                        )
                    }
                }
            }

            val soknaderTilSletting =
                listOf(
                    "824bf69c-0be0-3cbd-92fe-049e76223434",
                    "a9faa145-e508-335e-b6cf-b6e31164a468",
                    "4e126554-1b52-33b5-ac25-b34799fddbdd",
                )

            soknaderTilSletting.forEach {
                try {
                    val soknaden = hentSoknadService.finnSykepengesoknad(it)
                    if (soknaden.status != Soknadstatus.FREMTIDIG) {
                        throw RuntimeException("Søknad med id: $it har ikke status FREMTIDIG, kan ikke slettes.")
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
                }
            }
        }
    }
}
