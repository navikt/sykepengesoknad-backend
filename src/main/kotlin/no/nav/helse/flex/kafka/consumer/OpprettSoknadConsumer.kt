package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.logger
import no.nav.helse.flex.soknadsopprettelse.BehandleSykmeldingOgBestillAktivering
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class OpprettSoknadConsumer(
    private val behandleSykmeldingOgBestillAktivering: BehandleSykmeldingOgBestillAktivering
) {

    val log = logger()
    val sykmeldingId = "601fe42a-4673-4597-b5c3-8ef8299dd249"

    @KafkaListener(
        topics = [SYKMELDINGSENDT_TOPIC],
        id = "sykmelding-sendt-opprett-enkeltsoknad",
        idIsGroup = true,
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
    )
    fun listen(cr: ConsumerRecord<String, String?>, acknowledgment: Acknowledgment) {
        if (cr.key() != sykmeldingId) return

        log.info("Fant sykmelding $sykmeldingId")

        val melding = cr.value()?.tilSykmeldingKafkaMessage()
        behandleSykmeldingOgBestillAktivering.prosesserSykmelding(cr.key(), melding)
    }
}
