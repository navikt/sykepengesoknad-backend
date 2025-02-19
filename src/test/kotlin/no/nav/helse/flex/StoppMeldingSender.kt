package no.nav.helse.flex

import no.nav.helse.flex.frisktilarbeid.ArbeidssokerregisterStoppMelding
import no.nav.helse.flex.frisktilarbeid.asProducerRecordKey
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_STOPP_TOPIC
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.producer.ProducerRecord

fun TestOppsettInterfaces.sendStoppMelding(
    id: String,
    fnr: String,
) {
    val stoppMelding = ArbeidssokerregisterStoppMelding(id, fnr)

    kafkaProducer().send(
        ProducerRecord(
            ARBEIDSSOKERREGISTER_STOPP_TOPIC,
            stoppMelding.fnr.asProducerRecordKey(),
            stoppMelding.serialisertTilString(),
        ),
    ).get()
}
