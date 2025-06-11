package no.nav.helse.flex.client.inntektskomponenten

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.YearMonth
import java.util.concurrent.TimeUnit

@Service
class DebugService(
    private val inntektskomponentenClient: InntektskomponentenClient,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val leaderElection: LeaderElection,
) {
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 3600, timeUnit = TimeUnit.MINUTES)
    fun debugInntekt() {
        if (!leaderElection.isLeader()) {
            val fnr = sykepengesoknadRepository.findBySykepengesoknadUuid("9150a8a7-20f6-31e8-9def-a4123665ba95")!!.fnr
            val fom = YearMonth.of(2025, 2)
            val tom = YearMonth.of(2025, 5)
            val inntekter = inntektskomponentenClient.hentInntekter(fnr, fom, tom)

            log.info("Hentet inntekter for soknad: 9150a8a7-20f6-31e8-9def-a4123665ba95: ${inntekter.serialisertTilString()}")
        }
    }
}
