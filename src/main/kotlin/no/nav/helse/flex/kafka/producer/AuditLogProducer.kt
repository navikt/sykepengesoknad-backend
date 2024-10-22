package no.nav.helse.flex.kafka.producer

import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.kafka.AUDIT_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.springframework.stereotype.Component

@Component
class AuditLogProducer(val producer: KafkaProducer<String, String>) {
    val log = logger()

    fun lagAuditLog(auditEntry: AuditEntry) {
        try {
            producer.send(
                ProducerRecord(
                    AUDIT_TOPIC,
                    auditEntry.serialisertTilString(),
                ),
            ).get()
        } catch (e: Exception) {
            log.error("Klarte ikke publisere AuditEntry på kafka")
            throw AivenKafkaException(e)
        }
    }
}
