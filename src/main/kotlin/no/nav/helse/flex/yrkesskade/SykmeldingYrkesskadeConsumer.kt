package no.nav.helse.flex.yrkesskade

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.OBJECT_MAPPER
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class SykmeldingYrkesskadeConsumer(
    private val db: YrkesskadeSykmeldingRepository
) {

    val log = logger()

    @KafkaListener(
        topics = [SYKMELDING_OK_TOPIC, SYKMELDING_AVVIST_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"], // TODO fjern når vi er i prod
        id = "sykmelding-yrkesskade-consumer",
        idIsGroup = true
    )
    fun listen(cr: ConsumerRecord<String, String?>, acknowledgment: Acknowledgment) {
        prosesserKafkaMelding(cr.key(), cr.value())

        acknowledgment.acknowledge()
    }

    fun prosesserKafkaMelding(
        sykmeldingId: String,
        sykmelding: String?
    ) {
        val kafkaMessage = sykmelding?.readValue()

        if (kafkaMessage == null) {
            db.delete(
                YrkesskadeSykmeldingDbRecord(
                    sykmeldingId
                )
            )
        } else if (kafkaMessage.sykmelding.medisinskVurdering.yrkesskade) {
            db.insert(sykmeldingId)
        }
    }

    private fun String.readValue(): ReceivedSykmelding = OBJECT_MAPPER.readValue(this)

    data class ReceivedSykmelding(
        val sykmelding: Sykmelding
    ) {
        data class Sykmelding(
            val medisinskVurdering: MedisinskVurdering
        ) {
            data class MedisinskVurdering(
                val yrkesskade: Boolean
            )
        }
    }
}

const val SYKMELDING_OK_TOPIC = "teamsykmelding.ok-sykmelding"
const val SYKMELDING_AVVIST_TOPIC = "teamsykmelding.avvist-sykmelding"
