package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

class ArbeidsforholdFraInntektskomponentenHentingTest : FellesTestOppsett() {
    @Autowired
    lateinit var arbeidsforholdFraInntektskomponentenHenting: ArbeidsforholdFraInntektskomponentenHenting

    @Test
    fun `finner det ene arbeidstaker forholdet vi allerede vet om`() {
        arbeidsforholdFraInntektskomponentenHenting
            .hentArbeidsforhold(
                fnr = "11111234565",
                startSykeforlop = LocalDate.now(),
            ).filter { it.arbeidsforholdstype == Arbeidsforholdstype.ARBEIDSTAKER }
            .shouldHaveSize(1)
    }

    @Test
    fun `finner et frilanser arbeidsforhold`() {
        val frilanserArbeidsforholdet =
            arbeidsforholdFraInntektskomponentenHenting
                .hentArbeidsforhold(
                    fnr = "11111234565",
                    startSykeforlop = LocalDate.now(),
                ).filter { it.arbeidsforholdstype == Arbeidsforholdstype.FRILANSER }
        frilanserArbeidsforholdet.shouldHaveSize(1)
        frilanserArbeidsforholdet
            .first {
                it.arbeidsforholdstype == Arbeidsforholdstype.FRILANSER
            }.orgnummer `should be equal to` "999333667"
    }

    @Test
    fun `finner ett vi ikke vet om`() {
        arbeidsforholdFraInntektskomponentenHenting
            .hentArbeidsforhold(
                fnr = "11111234565",
                startSykeforlop = LocalDate.now(),
            ).filter { it.arbeidsforholdstype == Arbeidsforholdstype.ARBEIDSTAKER }
            .map { it.navn } `should be equal to`
            listOf("Bensinstasjonen AS")
    }

    @Test
    fun `finner to vi ikke vet om`() {
        arbeidsforholdFraInntektskomponentenHenting
            .hentArbeidsforhold(
                fnr = "22222222222",
                startSykeforlop = LocalDate.now(),
            ).filter { it.arbeidsforholdstype == Arbeidsforholdstype.ARBEIDSTAKER }
            .map { it.navn } `should be equal to` listOf("Bensinstasjonen AS", "Kiosken, avd Oslo AS")
    }

    @Test
    fun `utelater ikke frilansinntekt`() {
        arbeidsforholdFraInntektskomponentenHenting
            .hentArbeidsforhold(
                fnr = "3333333333",
                startSykeforlop = LocalDate.now(),
            ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS")
    }
}
