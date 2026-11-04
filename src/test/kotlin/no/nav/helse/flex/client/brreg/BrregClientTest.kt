package no.nav.helse.flex.client.brreg

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mockdispatcher.BrregMockDispatcher
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.util.serialisertTilString
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
                MockResponse(code = 200, body = """{"roller": []}""")
            },
        )

        val roller = brregClient.hentRoller("fnr", listOf(Rolletype.INNH))
        roller.size shouldBe 0
    }

    @Test
    fun `Kaster HttpServerErrorException når Brreg returnerer 5xx tre ganger`() {
        repeat(3) {
            BrregMockDispatcher.enqueueResponse(
                response("Feil på server", 500),
            )
        }

        shouldThrow<HttpServerErrorException> { brregClient.hentRoller("fnr", listOf(Rolletype.INNH)) }
    }

    @Test
    fun `Retryer successfully når Brreg returnerer 5xx to ganger`() {
        repeat(2) {
            BrregMockDispatcher.enqueueResponse(
                response("Feil på server", 500),
            )
        }

        brregClient.hentRoller("fnr", listOf(Rolletype.INNH)).size shouldBe 1
    }

    @Test
    fun `Håndterer feil når Brreg returnerer 4xx`() {
        BrregMockDispatcher.enqueueResponse(
            response("Feil på klient", 400),
        )

        val roller = brregClient.hentRoller("fnr", listOf(Rolletype.INNH))
        roller.size shouldBe 0
    }

    private fun response(
        feilmelding: String,
        responsKode: Int,
    ): MockResponse =
        withContentTypeApplicationJson {
            MockResponse(code = responsKode, body = mapOf("Message" to feilmelding).serialisertTilString())
        }
}
