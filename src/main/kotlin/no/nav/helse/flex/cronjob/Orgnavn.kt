package no.nav.helse.flex.cronjob

import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class Orgnavn(
    private val leaderElection: LeaderElection,
    private val repository: SykepengesoknadRepository,
    private val kafkaProducer: KafkaProducer<String, String>,
) {

    private val log = logger()

    @Scheduled(initialDelay = 5, fixedDelay = 100_000_000, timeUnit = TimeUnit.MINUTES)
    fun job() {
        if (leaderElection.isLeader()) {
            hentSisteOrgnavn()
        }
    }

    fun hentSisteOrgnavn() {
        val organisasjoner = repository.findLatestOrgnavn()

        log.info(organisasjoner.toString())

        organisasjoner.forEach {
            kafkaProducer.send(
                ProducerRecord(
                    "flex.organisasjoner",
                    it.first,
                    it.second
                )
            )
        }
    }
}
