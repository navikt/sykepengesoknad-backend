package no.nav.helse.flex.client.bregDirect

import no.nav.helse.flex.mockdispatcher.GrunnbeloepApiMockDispatcher
import no.nav.helse.flex.testoppsett.simpleDispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class EnhetsregisterClientTest {
    // private lateinit var mockWebServer: MockWebServer
    private lateinit var client: EnhetsregisterClient

    private val mockWebServer: MockWebServer =
        MockWebServer().apply {}
//  {
//            dispatcher = simpleDispatcher { MockResponse().setResponseCode(200) }
//        }
    private val restClient = RestClient.create()

    @BeforeEach
    fun setUp() {
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/").toString().removeSuffix("/")
        val rc = RestClient.builder()
            .baseUrl(baseUrl)
            .build()
        client = EnhetsregisterClient(enhetsregisterRestClient = rc)
    }


    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `erDagmamma returnerer NEI for ikke-dagmamma org`() {
        val json = """{"naeringskode1": {"kode": "41.109"}}"""
        mockWebServer.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))

        val status = client.erDagmamma("509100675")
        assertEquals(false, status)
    }

    @Test
    fun `erDagmamma returnerer JA for dagmamma org`() {
        val json = """{"naeringskode1": {"kode": "88.912"}}"""
        mockWebServer.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))

        val status = client.erDagmamma("922720193")
        assertEquals(true, status)
    }

//    @Test
//    fun `erDagmamma returnerer IKKE_FUNNET for 404 respons`() {
//        mockWebServer.enqueue(MockResponse().setResponseCode(404))
//
//        val status = client.erDagmamma("123456789")
//        assertEquals(DagmammaStatus.IKKE_FUNNET, status)
//    }

//    @Test
//    fun `erDagmamma returnerer SERVER_ERROR for 500 response`() {
//        // The retry mechanism is mocked to fail on the first attempt in a test context.
//        mockWebServer.enqueue(MockResponse().setResponseCode(500))
//
//        val status = client.erDagmamma("123456789")
//        assertEquals(DagmammaStatus.SERVER_FEIL, status)
//    }
}
