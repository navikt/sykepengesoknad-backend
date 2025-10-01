package no.nav.helse.flex.client.brreg

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mockdispatcher.BrregMockDispatcher
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.invoking
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException

@TestPropertySource(properties = ["CLIENT_RETRY_ATTEMPTS=1"])
class BrregClientTest : FellesTestOppsett() {
    @Autowired
    private lateinit var brregClient: BrregClient

    @Test
    fun `Kaster HttpClientErrorException når Brreg returnerer 4xx`() {
        BrregMockDispatcher.enqueueResponse(
            withContentTypeApplicationJson {
                MockResponse()
                    .setBody(
                        mapOf(
                            "Message" to "Feil med client",
                        ).serialisertTilString(),
                    ).setResponseCode(400)
            },
        )

        invoking { brregClient.hentRoller("fnr", listOf(Rolletype.INNH)) }
            .shouldThrow(HttpClientErrorException::class)
    }

    @Test
    fun `Kaster HttpServerErrorException når Brreg returnerer 5xx`() {
        BrregMockDispatcher.enqueueResponse(
            withContentTypeApplicationJson {
                MockResponse()
                    .setBody(
                        mapOf(
                            "Message" to "Feil på server",
                        ).serialisertTilString(),
                    ).setResponseCode(500)
            },
        )

        invoking { brregClient.hentRoller("fnr", listOf(Rolletype.INNH)) }
            .shouldThrow(HttpServerErrorException::class)
    }
}
