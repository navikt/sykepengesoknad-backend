package no.nav.helse.flex

import no.nav.helse.flex.kafka.SIS_TOPIC
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.vedtaksperiodebehandling.Behandlingstatusmelding
import org.apache.kafka.clients.producer.ProducerRecord

fun FellesTestOppsett.sendBehandlingsstatusMelding(behandlingstatusmelding: Behandlingstatusmelding) {
    kafkaProducer.send(
        ProducerRecord(
            SIS_TOPIC,
            behandlingstatusmelding.vedtaksperiodeId,
            behandlingstatusmelding.serialisertTilString(),
        ),
    ).get()
}
