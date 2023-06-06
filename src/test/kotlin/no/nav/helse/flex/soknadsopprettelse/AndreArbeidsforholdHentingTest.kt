package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.BaseTestClass
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class AndreArbeidsforholdHentingTest : BaseTestClass() {

    @Autowired
    lateinit var andreArbeidsforholdHenting: AndreArbeidsforholdHenting

    @Test
    fun `finner det ene vi allerede vet om`() {
        andreArbeidsforholdHenting.hentArbeidsforhold(
            fnr = "11111234565",
            arbeidsgiverOrgnummer = "999333666",
            startSykeforlop = LocalDate.now()
        ).`should be empty`()
    }

    @Test
    fun `finner ett vi ikke vet om`() {
        andreArbeidsforholdHenting.hentArbeidsforhold(
            fnr = "11111234565",
            arbeidsgiverOrgnummer = "999333667",
            startSykeforlop = LocalDate.now()
        ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS")
    }

    @Test
    fun `finner to vi ikke vet om`() {
        andreArbeidsforholdHenting.hentArbeidsforhold(
            fnr = "22222222222",
            arbeidsgiverOrgnummer = "999333667",
            startSykeforlop = LocalDate.now()
        ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS", "Kiosken, avd Oslo AS")
    }

    @Test
    fun `utelater ikke frilansinntekt`() {
        andreArbeidsforholdHenting.hentArbeidsforhold(
            fnr = "3333333333",
            arbeidsgiverOrgnummer = "99944736",
            startSykeforlop = LocalDate.now()
        ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS")
    }
}
