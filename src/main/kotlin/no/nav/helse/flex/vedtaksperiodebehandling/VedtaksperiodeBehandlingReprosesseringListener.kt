package no.nav.helse.flex.vedtaksperiodebehandling

import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import tools.jackson.module.kotlin.readValue

@Component
class VedtaksperiodeBehandlingReprosesseringListener(
    private val prosseserKafkaMeldingFraSpleiselaget: ProsseserKafkaMeldingFraSpleiselaget,
) {
    val log = logger()

    @KafkaListener(
        topics = [SIS_TOPIC],
        containerFactory = "seekAwareKafkaListenerContainerFactory",
        id = "sykepengesoknad-backend-vedtaksperiode-behandling-v2-1",
        idIsGroup = true,
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        val meldingMetadata: MeldingMetadata = objectMapper.readValue(cr.value())

        if (meldingMetadata.versjon?.startsWith("2.1") == true && meldingMetadata.eventName == "behandlingstatus") {
            log.info("SIS: Behandler melding med versjon ${meldingMetadata.versjon} og eventName ${meldingMetadata.eventName}")
            val kafkaDto: Behandlingstatusmelding = objectMapper.readValue(cr.value())
            prosseserKafkaMeldingFraSpleiselaget.prosesserKafkaMelding(kafkaDto)
        } else {
            log.info("SIS: Skipper melding med versjon ${meldingMetadata.versjon} og eventName ${meldingMetadata.eventName}")
        }

        acknowledgment.acknowledge()
    }
}
