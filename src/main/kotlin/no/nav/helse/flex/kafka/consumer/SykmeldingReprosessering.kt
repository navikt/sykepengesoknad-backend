package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.logger
import no.nav.helse.flex.soknadsopprettelse.BehandleSykmeldingOgBestillAktivering
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.common.TopicPartition
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.listener.ConsumerSeekAware
import org.springframework.kafka.listener.ConsumerSeekAware.ConsumerSeekCallback
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.collections.filterNotNull

@Component
class SykmeldingReprosessering(
    private val behandleSykmeldingOgBestillAktivering: BehandleSykmeldingOgBestillAktivering,
) : ConsumerSeekAware {
    private val log = logger()

    @KafkaListener(
        topics = [SYKMELDINGSENDT_TOPIC],
        id = "sykmelding-reprosesser-v3",
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(
        cr: ConsumerRecord<String, String>,
        acknowledgment: Acknowledgment,
    ) {
        if (cr.key() == "002287b8-4f6d-4eba-8705-965d8a28af40") {
            log.info("Reprosesserer sykmelding med id ${cr.key()}.")
            val sykmeldingKafkaMessage = cr.value()?.tilSykmeldingKafkaMessage()
            behandleSykmeldingOgBestillAktivering.prosesserSykmelding(cr.key(), sykmeldingKafkaMessage, cr.topic())
        }
        acknowledgment.acknowledge()
    }

    override fun onPartitionsAssigned(
        assignments: Map<TopicPartition?, Long?>,
        callback: ConsumerSeekCallback,
    ) {
        val startAt = LocalDate.of(2025, 12, 28).toInstantAtStartOfDay().toEpochMilli()

        assignments.keys.filterNotNull().forEach { topicPartition ->
            callback.seekToTimestamp(topicPartition.topic(), topicPartition.partition(), startAt)
        }
    }

    private fun LocalDate.toInstantAtStartOfDay(): Instant = this.atStartOfDay().toInstant(ZoneOffset.UTC)
}
