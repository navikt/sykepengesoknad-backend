package no.nav.helse.flex

import no.nav.helse.flex.frisktilarbeid.ArbeidssokerperiodeStartStoppMelding
import no.nav.helse.flex.frisktilarbeid.StartStopp.STOPP
import no.nav.helse.flex.frisktilarbeid.asProducerRecordKey
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_START_STOPP_TOPIC
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.Instant

fun TestOppsettInterfaces.sendStoppMelding(
    vedtaksperiodeId: String,
    fnr: String,
    tidspunkt: Instant,
) {
    val startStoppMelding =
        ArbeidssokerperiodeStartStoppMelding(
            operation = STOPP,
            vedtaksperiodeId = vedtaksperiodeId,
            fnr = fnr,
            tidspunkt = tidspunkt,
        )

    kafkaProducer()
        .send(
            ProducerRecord(
                ARBEIDSSOKERREGISTER_START_STOPP_TOPIC,
                startStoppMelding.fnr.asProducerRecordKey(),
                startStoppMelding.serialisertTilString(),
            ),
        ).get()
}
