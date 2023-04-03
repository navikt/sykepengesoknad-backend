package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.oppdatersporsmal.sykmelding.KorrigerteEgenmeldingsdager
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class RedigerteEgenmeldingsdagerConsumer(
    private val korrigerteEgenmeldingsdager: KorrigerteEgenmeldingsdager
) {

    @KafkaListener(
        topics = [SYKMELDINGSENDT_TOPIC],
        containerFactory = "aivenKafkaListenerContainerFactory",
        properties = ["auto.offset.reset = earliest"],
        id = "redigerte-egenmeldingsdager",
        idIsGroup = true
    )
    fun listen(cr: ConsumerRecord<String, String?>, acknowledgment: Acknowledgment) {
        val kafkaMessage = cr.value()?.tilSykmeldingKafkaMessage()

        if (kafkaMessage?.event?.erSvarOppdatering != true) {
            acknowledgment.acknowledge()
            return
        }

        korrigerteEgenmeldingsdager.ettersendSoknaderTilNav(kafkaMessage)

        acknowledgment.acknowledge()
    }
}
