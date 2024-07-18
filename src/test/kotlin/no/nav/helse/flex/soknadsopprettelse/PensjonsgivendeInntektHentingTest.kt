package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.inntektskomponenten.PensjongivendeInntektClient
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PensjonsgivendeInntektHentingTest : FellesTestOppsett() {
    @Autowired
    lateinit var pensjongivendeInntektClient: PensjongivendeInntektClient

    @Test
    fun `henter ut pensjonsgivende inntekt for fnr`() {
        // pensjongivendeInntektClient.hentPensjonsgivendeInntekter(fnr = "11111234565").let {
        // it.size `should be equal to` 3
        // it.first().pensjonsgivendeInntekt.pensjonsgivendeInntektAvNaeringsinntekt `should be equal to` 300000
        // }
    }
}
