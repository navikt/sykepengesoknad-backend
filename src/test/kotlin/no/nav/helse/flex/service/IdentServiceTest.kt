package no.nav.helse.flex.service

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class IdentServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var identService: IdentService

    @Test
    fun `Vi tester hent historikk for fnr`() {
        val responseData = identService.hentFolkeregisterIdenterMedHistorikkForFnr("31111111111")
        responseData.andreIdenter shouldBe listOf("11111111111", "21111111111")
        responseData.originalIdent shouldBe "31111111111"
    }
}
