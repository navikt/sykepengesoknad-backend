package no.nav.helse.flex.soknadsopprettelse

import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test

class PrettyOrgnavnTest {

    @Test
    fun `Gjør SKRIKENDE orgnavn pene`() {
        "NAV OSLO".prettyOrgnavn() `should be equal to` "NAV Oslo"
        "MATBUTIKKEN AS".prettyOrgnavn() `should be equal to` "Matbutikken AS"
        "COOP NORGE SA,AVD LAGER STAVANGER".prettyOrgnavn() `should be equal to` "Coop Norge SA, avd lager Stavanger"
    }
}
