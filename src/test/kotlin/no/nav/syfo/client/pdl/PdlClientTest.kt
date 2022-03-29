package no.nav.syfo.client.pdl

import no.nav.syfo.BaseTestClass
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class PdlClientTest : BaseTestClass() {

    @Autowired
    private lateinit var pdlClient: PdlClient

    @Test
    fun `Vi tester happycase`() {

        val responseData = pdlClient.hentIdenterMedHistorikk("31111111111")

        responseData.map { it.ident } `should be equal to` listOf("31111111111", "11111111111", "21111111111", "3111111111100")

        val takeRequest = pdlMockWebserver.takeRequest()
        takeRequest.headers["TEMA"] `should be equal to` "SYK"
        takeRequest.method `should be equal to` "POST"
        takeRequest.headers["Authorization"]!!.shouldStartWith("Bearer ey")
    }
}
