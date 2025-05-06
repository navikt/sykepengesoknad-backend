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
    avsluttetTidspunkt: Instant,
) {
    val stoppMelding = ArbeidssokerperiodeStoppMelding(vedtaksperiodeId, fnr, avsluttetTidspunkt)

    kafkaProducer()
        .send(
            ProducerRecord(
                ARBEIDSSOKERREGISTER_STOPP_TOPIC,
                stoppMelding.fnr.asProducerRecordKey(),
                stoppMelding.serialisertTilString(),
            ),
        ).get()
}
