package no.nav.helse.flex.client.bregDirect

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.RestClient
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EnhetsregisterClientTest {
    private lateinit var client: EnhetsregisterClient

    private val mockWebServer: MockWebServer =
        MockWebServer().apply {}

    @BeforeEach
    fun setUp() {
        mockWebServer.start()
        val baseUrl = mockWebServer.url("/").toString().removeSuffix("/")
        val rc =
            RestClient
                .builder()
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

        val erDagmamma = client.erDagmamma("509100675")
        assertFalse(erDagmamma)
    }

    @Test
    fun `erDagmamma returnerer JA for dagmamma org`() {
        val json = """{"naeringskode1": {"kode": "88.912"}}"""
        mockWebServer.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json"))

        val status = client.erDagmamma("922720193")
        assertTrue(status)
    }

}
