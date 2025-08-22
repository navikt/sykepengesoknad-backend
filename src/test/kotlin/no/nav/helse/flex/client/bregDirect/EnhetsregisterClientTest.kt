package no.nav.helse.flex.client.bregDirect

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient

class EnhetsregisterClientTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: EnhetsregisterClient

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        client =
            EnhetsregisterClient(
                restClientBuilder = RestClient.builder(),
                baseUrl = mockWebServer.url("/").toString(),
            )
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `erDagmamma returns NO for non-dagmamma org`() {
        val json = """{"naeringskode1": {"kode": "41.109"}}"""
        mockWebServer.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))

        val status = client.erDagmamma("509100675")
        assertEquals(DagmammaStatus.NO, status)
    }

    @Test
    fun `erDagmamma returns YES for dagmamma org`() {
        val json = """{"naeringskode1": {"kode": "88.912"}}"""
        mockWebServer.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))

        val status = client.erDagmamma("922720193")
        assertEquals(DagmammaStatus.YES, status)
    }

    @Test
    fun `erDagmamma returns NOT_FOUND for 404 response`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val status = client.erDagmamma("123456789")
        assertEquals(DagmammaStatus.NOT_FOUND, status)
    }

    @Test
    fun `erDagmamma returns SERVER_ERROR for 500 response`() {
        // The retry mechanism is mocked to fail on the first attempt in a test context.
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val status = client.erDagmamma("123456789")
        assertEquals(DagmammaStatus.SERVER_ERROR, status)
    }
}
