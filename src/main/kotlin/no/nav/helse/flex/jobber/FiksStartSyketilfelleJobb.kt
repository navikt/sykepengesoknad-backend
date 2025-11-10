package no.nav.helse.flex.jobber

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@Component
class FixStartSyketilfelleJobb(
    val leaderElection: LeaderElection,
    val sykepengesoknadRepository: SykepengesoknadRepository,
) {
    private val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
    fun startFixStartSyketilfelleJobb() {
        log.info("Kjører scheduled jobb FixStartSyketilfelleJobb.")
        if (leaderElection.isLeader()) {
            log.info("Er leader starter jobb FixStartSyketilfelleJobb.")
            fiks()
        } else {
            log.info("Er ikke leader, starter ikke jobb FixStartSyketilfelleJobb.")
        }
    }

    private fun fiks() {
        SOKNAD_IDER_SOM_SKAL_FIKSES.forEach { berortSoknadId ->
            val soknad = sykepengesoknadRepository.findBySykepengesoknadUuid(berortSoknadId)!!
            val gammeltStartSykeforlop = LocalDate.of(2002, 10, 20)
            val nyttStartSykeforlop = LocalDate.of(2025, 9, 8)
            @Suppress("IDENTITY_SENSITIVE_OPERATIONS_WITH_VALUE_TYPE")
            if (soknad.startSykeforlop == gammeltStartSykeforlop) {
                sykepengesoknadRepository.save(
                    soknad.copy(
                        startSykeforlop = nyttStartSykeforlop,
                        forstegangssoknad = false,
                    ),
                )
                log.info("Oppdaterte startSykeforlop for $berortSoknadId fra $gammeltStartSykeforlop til $nyttStartSykeforlop")
            }
        }
        log.info("Ferdig med scheduled jobb FixStartSyketilfelleJobb.")
    }
}

val SOKNAD_IDER_SOM_SKAL_FIKSES =
    listOf(
        "0459de06-61b4-3db4-a13a-da75d9b29a76",
        "ef2e9364-9fa1-321e-94f3-b393a5b6895f",
    )
