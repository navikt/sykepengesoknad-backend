package no.nav.helse.flex.vedtaksperiodebehandling

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class VedtaksperiodeBehandlingConsumer(
    private val prosseserKafkaMeldingFraSpleiselaget: ProsseserKafkaMeldingFraSpleiselaget,
) {
    val log = logger()

    @KafkaListener(
        topics = [SIS_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        id = "sis-consumer",
        idIsGroup = false,
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val versjonsmelding: MeldingMedVersjon = objectMapper.readValue(cr.value())

        if (versjonsmelding.versjon?.startsWith("2.0.") == true) {
            val kafkaDto: Behandlingstatusmelding = objectMapper.readValue(cr.value())
            prosseserKafkaMeldingFraSpleiselaget.prosesserKafkaMelding(kafkaDto)
        }

        acknowledgment.acknowledge()
    }
}
