package no.nav.syfo.kafka.consumer

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.logger
import no.nav.syfo.repository.RedusertVenteperiodeDbRecord
import no.nav.syfo.repository.RedusertVenteperiodeRepository
import no.nav.syfo.util.OBJECT_MAPPER
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
        id = "redusert-venteperiode-consumer",
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
