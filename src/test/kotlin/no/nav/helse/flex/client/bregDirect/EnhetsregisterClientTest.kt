package no.nav.helse.flex.client.bregDirect

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier

class EnhetsregisterClientTest : FakesTestOppsett() {
    @Autowired
    private lateinit var enhetsregisterClient: EnhetsregisterClient

    @Autowired
    @Qualifier("enhetsregisterMockWebServer")
    lateinit var enhetsregisterMockWebServer: MockWebServer

    @Test
    fun `Sykmeldt er ikke barnepasser som innehaver organisasjon`() {
        val json = """{"naeringskode1": {"kode": "41.109"}}"""
        enhetsregisterMockWebServer.enqueue(withContentTypeApplicationJson { MockResponse().setBody(json) })

        enhetsregisterClient.erBarnepasser("999999999") `should be equal to` false
    }

    @Test
    fun `Sykmeldt er barnepasser som innehaver av organisasjon`() {
        val json = """{"naeringskode1": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        enhetsregisterMockWebServer.enqueue(withContentTypeApplicationJson { MockResponse().setBody(json) })

        enhetsregisterClient.erBarnepasser("999999999") `should be equal to` true
    }

    @Test
    fun `Sykmeldt er barnepasser som innehaver av organisasjon med flere næringskoder`() {
        val json = """{"naeringskode1": {"kode": "41.109"}, "naeringskode2": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        enhetsregisterMockWebServer.enqueue(withContentTypeApplicationJson { MockResponse().setBody(json) })

        enhetsregisterClient.erBarnepasser("999999999") `should be equal to` true
    }
}
