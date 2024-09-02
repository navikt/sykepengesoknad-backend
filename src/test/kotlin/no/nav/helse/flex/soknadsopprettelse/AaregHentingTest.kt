package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class AaregHentingTest : FellesTestOppsett() {
    @Autowired
    lateinit var aaregDataHenting: AaregDataHenting

    @Test
    fun `finner ingen nye arbeidsforhold`() {
        aaregDataHenting.hentNyeArbeidsforhold(
            fnr = "11111234565",
            arbeidsgiverOrgnummer = "999333666",
            startSykeforlop = LocalDate.now(),
        ).`should be empty`()
    }

    @Test
    fun `finner to nye arbeidsforhold`() {
        val nyeArbeidsforhold =
            aaregDataHenting.hentNyeArbeidsforhold(
                fnr = "22222220001",
                arbeidsgiverOrgnummer = "112233445",
                startSykeforlop = LocalDate.now().minusDays(50),
            )
        nyeArbeidsforhold.shouldHaveSize(2)
        nyeArbeidsforhold[0].navn `should be equal to` "Bensinstasjonen AS"
        nyeArbeidsforhold[1].navn `should be equal to` "Kiosken, avd Oslo AS"
    }
}