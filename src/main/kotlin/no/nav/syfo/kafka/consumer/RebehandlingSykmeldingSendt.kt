package no.nav.syfo.kafka.consumer

import no.nav.syfo.kafka.producer.BEHANDLINGSTIDSPUNKT
import no.nav.syfo.kafka.producer.RebehandlingSykmeldingSendtProducer
import no.nav.syfo.kafka.sykmeldingSendtRetryTopic
import no.nav.syfo.logger
import no.nav.syfo.soknadsopprettelse.BehandleSendtBekreftetSykmeldingService
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset

@Component
class RebehandlingSykmeldingSendt(
    private val rebehandlingSykmeldingSendtProducer: RebehandlingSykmeldingSendtProducer,
    private val behandleSendtBekreftetSykmeldingService: BehandleSendtBekreftetSykmeldingService
) {

    val log = logger()

    @KafkaListener(
        topics = [sykmeldingSendtRetryTopic],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = latest"],
        id = "sykmelding-retry",
        idIsGroup = false,
    )
    fun listen(cr: ConsumerRecord<String, String>, acknowledgment: Acknowledgment) {
        val sykmeldingKafkaMessage = cr.value().tilSykmeldingKafkaMessage()
        val behandlingstidspunkt = cr.headers().lastHeader(BEHANDLINGSTIDSPUNKT)
            ?.value()
            ?.let { String(it, StandardCharsets.UTF_8) }
            ?.let { Instant.ofEpochMilli(it.toLong()) }
            ?: Instant.now()

        try {
            val sovetid = behandlingstidspunkt.sovetid()
            if (sovetid > 0) {
                log.info(
                    "Mottok rebehandling av sykmelding ${sykmeldingKafkaMessage.event.sykmeldingId} med behandlingstidspunkt ${
                    behandlingstidspunkt.atOffset(
                        ZoneOffset.UTC
                    )
                    } sover i $sovetid millisekunder"
                )
                acknowledgment.nack(sovetid)
            } else {
                if (behandleSendtBekreftetSykmeldingService.prosesserSykmelding(cr.key(), sykmeldingKafkaMessage)) {
                    log.info("Sykmelding SENDT med id: ${sykmeldingKafkaMessage.event.sykmeldingId} fullførte rebehandling")
                }
                acknowledgment.acknowledge()
            }
        } catch (e: Exception) {
            rebehandlingSykmeldingSendtProducer.leggPaRebehandlingTopic(
                sykmeldingKafkaMessage,
                OffsetDateTime.now().plusMinutes(10)
            )
            log.error(
                "Uventet feil ved rebehandling av sykmelding ${sykmeldingKafkaMessage.event.sykmeldingId}, rebehandles om 10 minutter",
                e
            )

            acknowledgment.acknowledge()
        }
    }

    private fun Instant.sovetid(): Long {
        val sovetid = this.toEpochMilli() - Instant.now().toEpochMilli()
        val maxSovetid = 1000L * 60 * 4 // Skjer en rebalansering hvis den sover i mer enn 5 min

        if (sovetid > maxSovetid) {
            return maxSovetid
        }
        return sovetid
    }
}
