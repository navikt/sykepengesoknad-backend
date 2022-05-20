package no.nav.helse.flex.service

import no.nav.helse.flex.BaseTestClass
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
}
