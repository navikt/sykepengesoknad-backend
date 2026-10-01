package no.nav.helse.flex.client.bregDirect

import mockwebserver3.MockResponse
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.mockdispatcher.EnhetsregisterMockDispatcher
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EnhetsregisterClientTest : FakesTestOppsett() {
    @Autowired
    private lateinit var enhetsregisterClient: EnhetsregisterClient

    @Test
    fun `Sykmeldt er ikke barnepasser som innehaver organisasjon`() {
        val json = """{"naeringskode1": {"kode": "41.109"}}"""
        EnhetsregisterMockDispatcher.enqueueResponse(withContentTypeApplicationJson { MockResponse(body = json) })

        enhetsregisterClient.erBarnepasser("999999999") `should be equal to` false
    }

    @Test
    fun `Sykmeldt er barnepasser som innehaver av organisasjon`() {
        val json = """{"naeringskode1": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        EnhetsregisterMockDispatcher.enqueueResponse(withContentTypeApplicationJson { MockResponse(body = json) })

        enhetsregisterClient.erBarnepasser("999999999") `should be equal to` true
    }

    @Test
    fun `Sykmeldt er barnepasser som innehaver av organisasjon med flere næringskoder`() {
        val json = """{"naeringskode1": {"kode": "41.109"}, "naeringskode2": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        EnhetsregisterMockDispatcher.enqueueResponse(withContentTypeApplicationJson { MockResponse(body = json) })

        enhetsregisterClient.erBarnepasser("999999999") `should be equal to` true
    }
}
