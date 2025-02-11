package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_STOPP_TOPIC
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Test

class ArbeidssokerRegisterStoppConsumerTest : FellesTestOppsett() {
    @Test
    fun `Sender og mottar ArbeidssokerRegisterStopp`() {
        val arbeidsokerRegisterStopp = ArbeidssokerRegisterStopp("id", "fnr")

        kafkaProducer.send(
            ProducerRecord(
                ARBEIDSSOKERREGISTER_STOPP_TOPIC,
                arbeidsokerRegisterStopp.fnr.asProducerRecordKey(),
                arbeidsokerRegisterStopp.serialisertTilString(),
            ),
        ).get()

        arbeidssokerRegisterStoppConsumer.ventPåRecords(1).also {
            it.first().key() `should be equal to` arbeidsokerRegisterStopp.fnr.asProducerRecordKey()

            val arbeidssokerRegisterStopp = it.first().value().tilArbeidssokerRegisterStopp()

            arbeidssokerRegisterStopp.id `should be equal to` arbeidsokerRegisterStopp.id
            arbeidssokerRegisterStopp.fnr `should be equal to` arbeidsokerRegisterStopp.fnr
        }
    }
}
