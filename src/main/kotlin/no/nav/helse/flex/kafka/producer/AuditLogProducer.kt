package no.nav.helse.flex.kafka.producer

import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.kafka.AUDIT_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component
import java.util.*

@Component
class AuditLogProducer(
    val auditlogKafkaProducer: KafkaProducer<String, String>,
) {
    val log = logger()

    fun lagAuditLog(auditEntry: AuditEntry) {
        try {
            auditlogKafkaProducer
                .send(
                    ProducerRecord(
                        AUDIT_TOPIC,
                        UUID.randomUUID().toString(),
                        auditEntry.serialisertTilString(),
                    ),
                ).get()
        } catch (e: Exception) {
            log.error("Klarte ikke publisere AuditEntry på kafka")
            throw AivenKafkaException(e)
        }
    }
}
