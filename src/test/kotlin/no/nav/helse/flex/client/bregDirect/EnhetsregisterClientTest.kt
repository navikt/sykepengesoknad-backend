package no.nav.helse.flex.client.bregDirect

import io.kotest.matchers.shouldBe
import mockwebserver3.MockResponse
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.mockdispatcher.EnhetsregisterMockDispatcher
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class EnhetsregisterClientTest : FakesTestOppsett() {
    @Autowired
    private lateinit var enhetsregisterClient: EnhetsregisterClient

    @Test
    fun `Sykmeldt er ikke barnepasser som innehaver organisasjon`() {
        val json = """{"naeringskode1": {"kode": "41.109"}}"""
        EnhetsregisterMockDispatcher.enqueueResponse(withContentTypeApplicationJson { MockResponse(body = json) })

        enhetsregisterClient.erBarnepasser("999999999") shouldBe false
    }

    @Test
    fun `Sykmeldt er barnepasser som innehaver av organisasjon`() {
        val json = """{"naeringskode1": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        EnhetsregisterMockDispatcher.enqueueResponse(withContentTypeApplicationJson { MockResponse(body = json) })

        enhetsregisterClient.erBarnepasser("999999999") shouldBe true
    }

    @Test
    fun `Sykmeldt er barnepasser som innehaver av organisasjon med flere næringskoder`() {
        val json = """{"naeringskode1": {"kode": "41.109"}, "naeringskode2": {"kode": "$NAERINGSKODE_BARNEPASSER"}}"""
        EnhetsregisterMockDispatcher.enqueueResponse(withContentTypeApplicationJson { MockResponse(body = json) })

        enhetsregisterClient.erBarnepasser("999999999") shouldBe true
    }
}
