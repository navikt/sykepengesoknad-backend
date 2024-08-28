package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be empty`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class AaregHentingTest : FellesTestOppsett() {
    @Autowired
    lateinit var andreArbeidsforholdHenting: AaregDataHenting

    @Test
    fun `finner ingen nye arbeidsforhold`() {
        andreArbeidsforholdHenting.hentNyeArbeidsforhold(
            fnr = "11111234565",
            arbeidsgiverOrgnummer = "999333666",
            startSykeforlop = LocalDate.now(),
        ).`should be empty`()
    }
}
