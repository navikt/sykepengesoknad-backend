package no.nav.syfo.service

import no.nav.syfo.BaseTestClass
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class IdentServiceTest : BaseTestClass() {

    @Autowired
    private lateinit var identService: IdentService

    @Test
    fun `Vi tester hent historikk for fnr`() {
        val responseData = identService.hentFolkeregisterIdenterMedHistorikkForFnr("31111111111")
        responseData.andreIdenter `should be equal to` listOf("11111111111", "21111111111")
        responseData.originalIdent `should be equal to` "31111111111"
    }

    @Test
    fun `Vi tester hent for aktor`() {
        val responseData = identService.hentFolkeregisterIdenterMedHistorikkForAktorid("31111111111")
        responseData `should be equal to` listOf("31111111111", "11111111111", "21111111111")
    }
}
