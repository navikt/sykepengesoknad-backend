package no.nav.helse.flex

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.sending.finnSendtTidspunkt
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class OppdaterSendtJob(
    val sykepengesoknadDAO: SykepengesoknadDAO,
    val sykepengesoknadRepository: SykepengesoknadRepository,
    val leaderElection: LeaderElection,
) {

    private val log = logger()

    @Profile("batchupdate")
    @Scheduled(initialDelay = 60 * 3, fixedDelay = 5, timeUnit = TimeUnit.SECONDS)
    fun oppdaterSendtJob() {
        var antallOppdatert = 0
        var antallFeilet = 0
        if (leaderElection.isLeader()) {
            val soknader = sykepengesoknadRepository.finnSendteSoknaderUtenSendt(1000)

            if (soknader.isEmpty()) {
                return
            }

            soknader.forEach {
                try {
                    val sendtTidspunkt = it.finnSendtTidspunkt()
                    sykepengesoknadDAO.oppdaterMedSendt(it.id!!, sendtTidspunkt)
                    antallOppdatert++
                } catch (e: RuntimeException) {
                    log.warn("Kunne ikke oppdatere med sendt-tidspunkt for søknad med id ${it.id}", e)
                    antallFeilet++
                }
            }
            log.info("Oppdatert $antallOppdatert med sendt-tidspunkt. $antallFeilet feilet.")
        }
    }
}
