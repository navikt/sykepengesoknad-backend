package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class FriskTilArbeidOpprydningCronJob(
    private val leaderElection: LeaderElection,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) {
    private val log = logger()

    @Scheduled(initialDelay = 1, fixedDelay = 50000, timeUnit = TimeUnit.MINUTES)
    fun run() {
        if (leaderElection.isLeader()) {
            log.info("Er leder, starter opprydning av frisk til arbeid data som var feilregistrert.")
            opprydning()
        } else {
            log.info("Er ikke leder, kjører ikke behandling av FriskTilArbeidVedtakStatus.")
        }
    }

    fun opprydning() {
        val ftaVedtakIder = listOf("TODO", "TODO")

        ftaVedtakIder.forEach { id ->
            log.info("Rydder for vedtakId: $id")

            val soknader =
                sykepengesoknadRepository.findByFriskTilArbeidVedtakId(id)
                    .filter { it.friskTilArbeidVedtakId == id }
                    .filter { it.soknadstype == Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING }
                    .filter { it.status == Soknadstatus.NY || it.status == Soknadstatus.FREMTIDIG }
            log.info("Fant ${soknader.size} soknader for vedtakId: $id som skal slettes")

            soknader.forEach { soknadDbRecord ->

                val soknad = sykepengesoknadDAO.finnSykepengesoknad(soknadDbRecord.sykepengesoknadUuid)
                val soknadSomSlettes = soknad.copy(status = Soknadstatus.SLETTET)
                sykepengesoknadDAO.slettSoknad(soknadSomSlettes)
                soknadProducer.soknadEvent(soknadSomSlettes, null, false)
                log.info(
                    "Slettet søknad: ${soknad.id} på grunn av feilregistrert periode i FriskTilArbeidId $id.",
                )
            }

            friskTilArbeidRepository.findById(id).ifPresent {
                log.info("Sletter FriskTilArbeidVedtak med id $id")
                friskTilArbeidRepository.delete(it)
            }
        }
    }
}
