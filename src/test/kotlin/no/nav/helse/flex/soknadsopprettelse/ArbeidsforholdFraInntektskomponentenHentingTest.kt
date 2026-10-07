package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mockdispatcher.InntektskomponentenMockDispatcher
import no.nav.helse.flex.mockdispatcher.skapArbeidstakerOgFrilanserInntekter
import no.nav.helse.flex.mockdispatcher.skapHentInntekterResponse
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

private const val FNR = "11111234565"

class ArbeidsforholdFraInntektskomponentenHentingTest : FellesTestOppsett() {
    @Autowired
    lateinit var arbeidsforholdFraInntektskomponentenHenting: ArbeidsforholdFraInntektskomponentenHenting

    @Test
    fun `finner det ene arbeidstaker forholdet vi allerede vet om`() {
        InntektskomponentenMockDispatcher.enqueue(skapArbeidstakerOgFrilanserInntekter(FNR))

        arbeidsforholdFraInntektskomponentenHenting
            .hentArbeidsforhold(
                fnr = FNR,
                arbeidsgiverOrgnummer = "999333666",
                startSykeforlop = LocalDate.now(),
            ).filter { it.arbeidsforholdstype == Arbeidsforholdstype.ARBEIDSTAKER }
            .`should be empty`()
    }

    @Test
    fun `finner et frilanser arbeidsforhold`() {
        InntektskomponentenMockDispatcher.enqueue(skapArbeidstakerOgFrilanserInntekter(FNR))

        val frilanserArbeidsforholdet =
            arbeidsforholdFraInntektskomponentenHenting
                .hentArbeidsforhold(
                    fnr = FNR,
                    arbeidsgiverOrgnummer = "999333666",
                    startSykeforlop = LocalDate.now(),
                ).filter { it.arbeidsforholdstype == Arbeidsforholdstype.FRILANSER }
        frilanserArbeidsforholdet.shouldHaveSize(1)
        frilanserArbeidsforholdet.first().orgnummer `should be equal to` "999333667"
    }

    @Test
    fun `finner arbeidsforhold som ikke er sykemeldt fra`() {
        InntektskomponentenMockDispatcher.enqueue(skapArbeidstakerOgFrilanserInntekter(FNR))

        arbeidsforholdFraInntektskomponentenHenting
            .hentArbeidsforhold(
                fnr = FNR,
                arbeidsgiverOrgnummer = "123454543",
                startSykeforlop = LocalDate.now(),
            ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS", "Frilanseransetter AS")
    }

    @Test
    fun `finner to vi ikke vet om`() {
        val fnr = "22222222222"
        InntektskomponentenMockDispatcher.enqueue(
            skapHentInntekterResponse(
                fnr = fnr,
                loennsinntektOrgnumre = listOf("999333666", "999888777"),
            ),
        )

        arbeidsforholdFraInntektskomponentenHenting
            .hentArbeidsforhold(
                fnr = fnr,
                arbeidsgiverOrgnummer = "999333667",
                startSykeforlop = LocalDate.now(),
            ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS", "Kiosken, avd Oslo AS")
    }

    @Test
    fun `utelater ikke frilansinntekt`() {
        val fnr = "3333333333"
        InntektskomponentenMockDispatcher.enqueue(
            skapHentInntekterResponse(
                fnr = fnr,
                loennsinntektOrgnumre = listOf("999333666"),
                frilansOrgnumre = listOf("999333666"),
            ),
        )

        arbeidsforholdFraInntektskomponentenHenting
            .hentArbeidsforhold(
                fnr = fnr,
                arbeidsgiverOrgnummer = "99944736",
                startSykeforlop = LocalDate.now(),
            ).map { it.navn } `should be equal to` listOf("Bensinstasjonen AS")
    }

    @Test
    fun `orgnummer med baade frilans og ordinaert arbeidsforhold blir klassifisert som arbeidstaker`() {
        InntektskomponentenMockDispatcher.enqueue(
            skapHentInntekterResponse(
                fnr = FNR,
                loennsinntektOrgnumre = listOf("999333667"),
                frilansOrgnumre = listOf("999333667"),
                ordinaereArbeidsforholdOrgnumre = listOf("999333667"),
            ),
        )

        val arbeidsforhold =
            arbeidsforholdFraInntektskomponentenHenting
                .hentArbeidsforhold(
                    fnr = FNR,
                    arbeidsgiverOrgnummer = "123454543",
                    startSykeforlop = LocalDate.now(),
                )
        arbeidsforhold.shouldHaveSize(1)
        arbeidsforhold.first().orgnummer `should be equal to` "999333667"
        arbeidsforhold.first().arbeidsforholdstype `should be equal to` Arbeidsforholdstype.ARBEIDSTAKER
    }
}
