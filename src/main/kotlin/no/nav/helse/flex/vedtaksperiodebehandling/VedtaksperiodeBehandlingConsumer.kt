package no.nav.helse.flex.vedtaksperiodebehandling

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.LocalDateTime
import java.time.ZoneId

@Component
class VedtaksperiodeBehandlingConsumer(
    private val prosseserKafkaMeldingFraSpleiselaget: ProsseserKafkaMeldingFraSpleiselaget,
) : ConsumerSeekAware {
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

    override fun onPartitionsAssigned(
        partitions: Map<TopicPartition, Long>,
        callback: ConsumerSeekAware.ConsumerSeekCallback,
    ) {
        val desiredDate = LocalDateTime.of(2025, 2, 16, 0, 0)
        val timestamp = desiredDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        log.info("Søker til $desiredDate for topic $SIS_TOPIC")
        callback.seekToTimestamp(partitions.keys, timestamp)
    }
}
