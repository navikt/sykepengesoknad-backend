package no.nav.helse.flex.kafka.consumer

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.repository.RedusertVenteperiodeDbRecord
import no.nav.helse.flex.repository.RedusertVenteperiodeRepository
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.logger
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class RedusertVenteperiodeConsumer(
    private val db: RedusertVenteperiodeRepository,
) {

    val log = logger()

    @KafkaListener(
        topics = [SYKMELDINGBEKREFTET_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "redusert-venteperiode-consumer-2",
        idIsGroup = true,
    )
    fun listen(cr: ConsumerRecord<String, String?>, acknowledgment: Acknowledgment) {
        prosesserKafkaMelding(cr.key(), cr.value())

        acknowledgment.acknowledge()
    }

    fun prosesserKafkaMelding(
        sykmeldingId: String,
        sykmeldingKafkaMessage: String?,
    ) {
        val kafkaMessage = sykmeldingKafkaMessage?.readValue()

        if (kafkaMessage == null) {
            db.delete(
                RedusertVenteperiodeDbRecord(
                    sykmeldingId
                )
            )
        } else if (kafkaMessage.sykmelding.harRedusertArbeidsgiverperiode) {
            db.insert(sykmeldingId)
        }
    }

    private fun String.readValue(): KafkaMelding = OBJECT_MAPPER.readValue(this)

    private data class KafkaMelding(
        val sykmelding: Sykmelding
    ) {
        data class Sykmelding(
            val harRedusertArbeidsgiverperiode: Boolean
        )
    }
}
