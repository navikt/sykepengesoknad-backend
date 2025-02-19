package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.sendStoppMelding
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test

class ArbeidssokerregisterStoppConsumerTest : FellesTestOppsett() {
    @Test
    fun `Sender og mottar ArbeidssokerregisterStoppMelding`() {
        val id = "id"
        val fnr = "fnr"
        sendStoppMelding(id, fnr)

        arbeidssokerregisterStoppConsumer.ventPåRecords(1).also {
            it.first().key() `should be equal to` fnr.asProducerRecordKey()

            val arbeidssokerRegisterStopp = it.first().value().tilArbeidssokerregisterStoppMelding()

            arbeidssokerRegisterStopp.id `should be equal to` id
            arbeidssokerRegisterStopp.fnr `should be equal to` fnr
        }
    }
}
