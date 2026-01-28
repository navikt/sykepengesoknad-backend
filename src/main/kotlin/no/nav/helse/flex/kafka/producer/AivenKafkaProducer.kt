package no.nav.helse.flex.kafka.producer

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.kafka.SYKEPENGESOKNAD_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.stereotype.Component

@Component
class AivenKafkaProducer(
    private val producer: Producer<String, SykepengesoknadDTO>,
    private val environmentToggles: EnvironmentToggles,
) {
    val log = logger()

    @WithSpan
    fun produserMelding(soknad: SykepengesoknadDTO): RecordMetadata {
        try {
            if (environmentToggles.isQ() || soknad.status == SoknadsstatusDTO.SLETTET || soknad.status == SoknadsstatusDTO.AVBRUTT) {
                log.info("Publiserer søknad: ${soknad.id} med status: ${soknad.status} på topic: $SYKEPENGESOKNAD_TOPIC.")
            }

            return producer
                .send(
                    ProducerRecord(
                        SYKEPENGESOKNAD_TOPIC,
                        soknad.id,
                        soknad,
                    ),
                ).get()
        } catch (e: Throwable) {
            log.error("Uventet exception ved publisering av søknad ${soknad.id} på topic $SYKEPENGESOKNAD_TOPIC", e)
            // get() kaster InterruptedException eller ExecutionException. Begge er checked, så pakker  de den inn i
            // en RuntimeException da en CheckedException kan forhindre rollback i metoder annotert med @Transactional.
            throw AivenKafkaException(e)
        }
    }
}

class AivenKafkaException(
    e: Throwable,
) : RuntimeException(e)
