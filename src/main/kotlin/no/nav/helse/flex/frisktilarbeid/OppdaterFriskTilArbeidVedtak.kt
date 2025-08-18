package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

@Service
class OppdaterFriskTilArbeidVedtak(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
    private val leaderElection: LeaderElection,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun settOverlappTilNy() {
        if (leaderElection.isLeader()) {
            friskTilArbeidRepository.oppdaterStatusTilNyForId("a69c3eb5-5b88-44fc-a54e-a870eaca9d8c")
            log.info("Oppdatert status til NY for FriskTilArbeidVedtakStatus: a69c3eb5-5b88-44fc-a54e-a870eaca9d8c.")
        }
    }
}
