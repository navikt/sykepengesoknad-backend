package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_STOPP_TOPIC
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Test

class ArbeidssokerregisterStoppConsumerTest : FellesTestOppsett() {
    @Test
    fun `Sender og mottar ArbeidssokerregisterStoppMelding`() {
        val stoppMelding = ArbeidssokerregisterStoppMelding("id", "fnr")

        kafkaProducer.send(
            ProducerRecord(
                ARBEIDSSOKERREGISTER_STOPP_TOPIC,
                stoppMelding.fnr.asProducerRecordKey(),
                stoppMelding.serialisertTilString(),
            ),
        ).get()

        arbeidssokerregisterStoppConsumer.ventPåRecords(1).also {
            it.first().key() `should be equal to` stoppMelding.fnr.asProducerRecordKey()

            val arbeidssokerRegisterStopp = it.first().value().tilArbeidssokerregisterStoppMelding()

            arbeidssokerRegisterStopp.id `should be equal to` stoppMelding.id
            arbeidssokerRegisterStopp.fnr `should be equal to` stoppMelding.fnr
        }
    }
}
