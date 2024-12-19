package no.nav.helse.flex.sending
import no.nav.helse.flex.cronjob.LeaderElection
import no.nav.helse.flex.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.helse.flex.kafka.producer.AivenKafkaProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class RepubliserBomlo(
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val sykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper,
    private val soknadProducer: AivenKafkaProducer,
    private val leaderElection: LeaderElection,
) {
    private val tilBomlo =
        listOf(
            "052504a7-3fbc-3832-8f34-e8334a84fc0d",
        )
    private val log = logger()

    @Scheduled(initialDelay = 3, fixedDelay = 60 * 30, timeUnit = TimeUnit.SECONDS)
    fun republiserBomlo() {
        if (!leaderElection.isLeader()) {
            return
        }
        tilBomlo.forEach {
            sykepengesoknadDAO.finnSykepengesoknad(it).let {
                log.info("Republiserer søknad $it til Bømlo.")
                soknadProducer.produserMelding(
                    sykepengesoknadDTOMapper.mapTilSykepengesoknadDTO(it),
                )
            }
        }
    }
}
