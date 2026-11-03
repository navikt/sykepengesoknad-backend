package no.nav.helse.flex.soknadsopprettelse

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class PrettyOrgnavnTest {
    @Test
    fun `Gjør SKRIKENDE orgnavn pene`() {
        "NAV OSLO".prettyOrgnavn() shouldBe "NAV Oslo"
        "MATBUTIKKEN AS".prettyOrgnavn() shouldBe "Matbutikken AS"
        "COOP NORGE SA,AVD LAGER STAVANGER".prettyOrgnavn() shouldBe "Coop Norge SA, avd lager Stavanger"
    }
}
