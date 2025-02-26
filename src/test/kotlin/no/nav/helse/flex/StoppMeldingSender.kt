package no.nav.helse.flex

import no.nav.helse.flex.frisktilarbeid.ArbeidssokerperiodeStoppMelding
import no.nav.helse.flex.frisktilarbeid.asProducerRecordKey
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_STOPP_TOPIC
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant

fun TestOppsettInterfaces.sendStoppMelding(
    vedtaksperiodeId: String,
    fnr: String,
) {
    val stoppMelding = ArbeidssokerperiodeStoppMelding(vedtaksperiodeId, fnr, Instant.now())

    kafkaProducer().send(
        ProducerRecord(
            ARBEIDSSOKERREGISTER_STOPP_TOPIC,
            stoppMelding.fnr.asProducerRecordKey(),
            stoppMelding.serialisertTilString(),
        ),
    ).get()
}
