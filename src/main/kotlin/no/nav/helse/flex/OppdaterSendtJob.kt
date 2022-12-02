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

@Component
class OppdaterSendtJob(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val sykepengesoknadRepository: SykepengesoknadRepository,
    val leaderElection: LeaderElection,
) {

    private val log = logger()

    var antallOppdatert = AtomicInteger(0)
    var antallFeilet = AtomicInteger(0)

    @Profile("batchupdate")
    @Scheduled(initialDelay = 60 * 3, fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    fun oppdaterSendtJob() {

        if (leaderElection.isLeader()) {
            val soknader = sykepengesoknadRepository.finnSendteSoknaderUtenSendt(1000)

            if (soknader.isEmpty()) {
                return
            }

            soknader.forEach {
                try {
                    val sendtTidspunkt = it.finnSendtTidspunkt()
                    sykepengesoknadDAO.oppdaterMedSendt(it.id!!, sendtTidspunkt)
                    antallOppdatert.incrementAndGet()
                } catch (e: RuntimeException) {
                    log.warn("Kunne ikke oppdatere med sendt-tidspunkt for søknad med id ${it.id}", e)
                    antallFeilet.incrementAndGet()
                }
            }
            if (antallOppdatert.toInt() % 100 == 0) {
                log.info("Oppdatert $antallOppdatert søknadder med sendt-tidspunkt. $antallFeilet feilet.")
            }
        }
    }
}
