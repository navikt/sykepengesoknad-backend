package no.nav.helse.flex.testdata

import com.fasterxml.jackson.module.kotlin.readValue
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidCronJob
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Profile
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
@Profile("testdata")
class CommandListener(
    val sykepengesoknadDAO: SykepengesoknadDAO,
) {
    val log = logger()

    @Autowired
    lateinit var friskTilArbeidCronJob: FriskTilArbeidCronJob

    @WithSpan
    @KafkaListener(
        topics = [COMMAND_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        data class Command(
            val command: String,
        )

        val req: Command = cr.value().let { objectMapper.readValue(it) }

        log.info("Mottok kommando ${req.command}")
        when (req.command) {
            "fta-cronjob" -> friskTilArbeidCronJob.behandleFriskTilArbeidVedtak()
            else -> log.warn("Ukjent kommando ${req.command}")
        }

        acknowledgment.acknowledge()
    }
}

const val COMMAND_TOPIC = "flex.test-command"
