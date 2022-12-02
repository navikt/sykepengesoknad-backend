package no.nav.helse.flex

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.sending.finnSendtTidspunkt
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val antall = 2000

@Component
class OppdaterSendtJob(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val sykepengesoknadRepository: SykepengesoknadRepository,
    val leaderElection: LeaderElection,
) {

    private val log = logger()

    var antallOppdatert = AtomicInteger(0)

    @Profile("batchupdate")
    @Scheduled(initialDelay = 60 * 3, fixedDelay = 1, timeUnit = TimeUnit.SECONDS)
    fun oppdaterSendtJob() {

        if (leaderElection.isLeader()) {
            val soknader = sykepengesoknadRepository.finnSendteSoknaderUtenSendt(antall)

            if (soknader.isEmpty()) {
                return
            }

            try {
                sykepengesoknadDAO.oppdaterMedSendt(soknader.map { Pair(it.id!!, it.finnSendtTidspunkt()) })
                antallOppdatert.addAndGet(antall)
            } catch (e: Exception) {
                log.warn("Feilet med batch-oppdatering av sendt-verdi.", e)
            }

            if (antallOppdatert.toInt() % antall * 10 == 0) {
                log.info("Oppdatert $antallOppdatert søknadder med sendt-tidspunkt.")
            }
        }
    }
}
