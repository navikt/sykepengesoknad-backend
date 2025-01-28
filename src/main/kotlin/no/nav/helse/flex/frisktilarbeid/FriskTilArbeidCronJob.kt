package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

const val BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK = 10

@Profile("frisktilarbeid")
@Component
class FriskTilArbeidCronJob(
    private val leaderElection: LeaderElection,
    private val friskTilArbeidService: FriskTilArbeidService,
) {
    private val log = logger()

    @Scheduled(initialDelay = 2, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun startBehandlingAvFriskTilArbeidVedtakStatus() {
        if (leaderElection.isLeader()) {
            log.info("Er leder, starter behandling av FriskTilArbeidVedtakStatus.")
            friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(BEHANDLE_ANTALL_FRISK_TIL_ARBEID_VEDTAK)
        } else {
            log.info("Er ikke leder, kjører ikke behandling av FriskTilArbeidVedtakStatus.")
        }
    }
}
