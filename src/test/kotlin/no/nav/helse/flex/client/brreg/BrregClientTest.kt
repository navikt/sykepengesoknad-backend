package no.nav.helse.flex.client.brreg

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.testoppsett.simpleDispatcher
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

class BrregClientTest : FellesTestOppsett() {
    @Autowired
    private lateinit var brregClient: BrregClient

    @Test
    fun `burde kaste client feil fra brreg`() {
        brregMockWebServer.dispatcher =
            simpleDispatcher {
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        mapOf(
                            "Message" to "Feil med client",
                        ).serialisertTilString(),
                    )
                    .setResponseCode(400)
            }

        invoking { brregClient.hentRoller("fnr", listOf(Rolletype.INNH)) }
            .shouldThrow(HttpClientErrorException::class)
    }

    @Test
    fun `burde kaste server feil fra brreg`() {
        brregMockWebServer.dispatcher =
            simpleDispatcher {
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        mapOf(
                            "Message" to "Feil på server",
                        ).serialisertTilString(),
                    )
                    .setResponseCode(500)
            }

        invoking { brregClient.hentRoller("fnr", listOf(Rolletype.INNH)) }
            .shouldThrow(HttpServerErrorException::class)
    }
}
