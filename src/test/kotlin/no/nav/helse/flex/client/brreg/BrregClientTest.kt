package no.nav.helse.flex.client.brreg

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mockdispatcher.BrregMockDispatcher
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpServerErrorException

class BrregClientTest : FellesTestOppsett() {
    @Autowired
    private lateinit var brregClient: BrregClient

    @Test
    fun `Håndterer tom liste når person ikke har roller`() {
        BrregMockDispatcher.enqueueResponse(
            withContentTypeApplicationJson {
                MockResponse()
                    .setBody("""{"roller": []}""")
                    .setResponseCode(200)
            },
        )

        val roller = brregClient.hentRoller("fnr", listOf(Rolletype.INNH))
        roller.size `should be equal to` 0
    }

    @Test
    fun `Kaster HttpServerErrorException når Brreg returnerer 5xx tre ganger`() {
        repeat(3) {
            BrregMockDispatcher.enqueueResponse(
                response("Feil på server", 500),
            )
        }

        invoking { brregClient.hentRoller("fnr", listOf(Rolletype.INNH)) }.shouldThrow(HttpServerErrorException::class)
    }

    @Test
    fun `Retryer successfully når Brreg returnerer 5xx to ganger`() {
        repeat(2) {
            BrregMockDispatcher.enqueueResponse(
                response("Feil på server", 500),
            )
        }

        brregClient.hentRoller("fnr", listOf(Rolletype.INNH)).size `should be equal to` 1
    }

    private fun response(
        feilmelding: String,
        responsKode: Int,
    ): MockResponse =
        withContentTypeApplicationJson {
            MockResponse()
                .setBody(mapOf("Message" to feilmelding).serialisertTilString())
                .setResponseCode(responsKode)
        }
}
