package no.nav.helse.flex

import no.nav.helse.flex.frisktilarbeid.ArbeidssokerperiodeStartStoppMelding
import no.nav.helse.flex.frisktilarbeid.StartStopp.START
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
    val stoppMelding = ArbeidssokerperiodeStartStoppMelding(STOPP, vedtaksperiodeId, fnr, tidspunkt)

    kafkaProducer()
        .send(
            ProducerRecord(
                ARBEIDSSOKERREGISTER_START_STOPP_TOPIC,
                stoppMelding.fnr.asProducerRecordKey(),
                stoppMelding.serialisertTilString(),
            ),
        ).get()
}

fun TestOppsettInterfaces.sendStartMelding(
    vedtaksperiodeId: String,
    fnr: String,
    tidspunkt: Instant,
) {
    val stoppMelding = ArbeidssokerperiodeStartStoppMelding(START, vedtaksperiodeId, fnr, tidspunkt)

    kafkaProducer()
        .send(
            ProducerRecord(
                ARBEIDSSOKERREGISTER_START_STOPP_TOPIC,
                stoppMelding.fnr.asProducerRecordKey(),
                stoppMelding.serialisertTilString(),
            ),
        ).get()
}
