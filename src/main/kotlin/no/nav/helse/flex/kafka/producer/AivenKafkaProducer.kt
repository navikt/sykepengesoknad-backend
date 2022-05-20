package no.nav.helse.flex.kafka.producer

import no.nav.helse.flex.kafka.sykepengesoknadTopic
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.syfo.logger
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.stereotype.Component

@Component
class AivenKafkaProducer(
    private val producer: KafkaProducer<String, SykepengesoknadDTO>
) {
    val log = logger()

    fun produserMelding(soknad: SykepengesoknadDTO): RecordMetadata {
        try {
            return producer.send(
                ProducerRecord(
                    sykepengesoknadTopic,
                    soknad.id,
                    soknad
                )
            ).get()
        } catch (e: Throwable) {
            log.error("Uventet exception ved publisering av søknad ${soknad.id} på topic $sykepengesoknadTopic", e)
            // get() kaster InterruptedException eller ExecutionException. Begge er checked, så pakker  de den inn i
            // en RuntimeException da en CheckedException kan forhindre rollback i metoder annotert med @Transactional.
            throw AivenKafkaException(e)
        }
    }
}

class AivenKafkaException(e: Throwable) : RuntimeException(e)
