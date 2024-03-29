package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class AndreArbeidsforholdHentingTest : FellesTestOppsett() {
    @Autowired
    lateinit var andreArbeidsforholdHenting: AndreArbeidsforholdHenting

    @Test
    fun `finner det ene arbeidstaker forholdet vi allerede vet om`() {
        andreArbeidsforholdHenting.hentArbeidsforhold(
            fnr = "11111234565",
            arbeidsgiverOrgnummer = "999333666",
            startSykeforlop = LocalDate.now(),
        ).filter { it.arbeidsforholdstype == Arbeidsforholdstype.ARBEIDSTAKER }.`should be empty`()
    }

    @Test
    fun `finner et frilanser arbeidsforhold`() {
        val frilanserArbeidsforholdet =
            andreArbeidsforholdHenting.hentArbeidsforhold(
                fnr = "11111234565",
                arbeidsgiverOrgnummer = "999333666",
                startSykeforlop = LocalDate.now(),
            ).filter { it.arbeidsforholdstype == Arbeidsforholdstype.FRILANSER }
        frilanserArbeidsforholdet.shouldHaveSize(1)
        frilanserArbeidsforholdet.first().orgnummer `should be equal to` "999333667"
    }

    @Test
    fun `finner ett vi ikke vet om`() {
        andreArbeidsforholdHenting.hentArbeidsforhold(
            fnr = "11111234565",
            arbeidsgiverOrgnummer = "999333667",
            startSykeforlop = LocalDate.now(),
        ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS")
    }

    @Test
    fun `finner to vi ikke vet om`() {
        andreArbeidsforholdHenting.hentArbeidsforhold(
            fnr = "22222222222",
            arbeidsgiverOrgnummer = "999333667",
            startSykeforlop = LocalDate.now(),
        ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS", "Kiosken, avd Oslo AS")
    }

    @Test
    fun `utelater ikke frilansinntekt`() {
        andreArbeidsforholdHenting.hentArbeidsforhold(
            fnr = "3333333333",
            arbeidsgiverOrgnummer = "99944736",
            startSykeforlop = LocalDate.now(),
        ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS")
    }
}
