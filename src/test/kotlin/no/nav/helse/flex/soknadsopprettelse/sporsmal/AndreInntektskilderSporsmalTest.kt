package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.nyttarbeidsforhold.tilAndreInntektskilderMetadata
import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraInntektskomponenten
import no.nav.helse.flex.soknadsopprettelse.Arbeidsforholdstype
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import java.time.LocalDate

class AndreInntektskilderSporsmalTest {
    @Test
    fun `takler ingen data fra aareg`() {
        val sporsmal =
            andreInntektskilderArbeidstakerV2(
                sykmeldingOrgnavn = "Politiet",
                sykmeldingOrgnr = "111",
                andreKjenteArbeidsforholdFraInntektskomponenten =
                    listOf(
                        ArbeidsforholdFraInntektskomponenten(
                            navn = "Brannvesenet",
                            orgnummer = "222",
                            arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER,
                        ),
                    ),
                nyeArbeidsforholdFraAareg = null,
            )
        sporsmal.sporsmalstekst `should be equal to` "Har du andre inntektskilder enn Politiet og Brannvesenet?"
        sporsmal.metadata!!.tilAndreInntektskilderMetadata().kjenteInntektskilder `should be equal to`
            listOf(
                KjentInntektskilde(
                    navn = "Politiet",
                    kilde = Kilde.SYKMELDING,
                    orgnummer = "111",
                ),
                KjentInntektskilde(
                    navn = "Brannvesenet",
                    kilde = Kilde.INNTEKTSKOMPONENTEN,
                    orgnummer = "222",
                ),
            )
    }

    @Test
    fun `filterer vekk data fra aareg hvis vi fant i inntektskomponenten`() {
        val sporsmal =
            andreInntektskilderArbeidstakerV2(
                sykmeldingOrgnavn = "Politiet",
                sykmeldingOrgnr = "111",
                andreKjenteArbeidsforholdFraInntektskomponenten =
                    listOf(
                        ArbeidsforholdFraInntektskomponenten(
                            navn = "Brannvesenet",
                            orgnummer = "222",
                            arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER,
                        ),
                    ),
                nyeArbeidsforholdFraAareg =
                    listOf(
                        ArbeidsforholdFraAAreg(
                            arbeidsstedNavn = "Brannvesenet",
                            arbeidsstedOrgnummer = "222",
                            startdato = LocalDate.now(),
                            opplysningspliktigOrgnummer = "333",
                            sluttdato = null,
                        ),
                        ArbeidsforholdFraAAreg(
                            arbeidsstedNavn = "Sykebilen",
                            arbeidsstedOrgnummer = "876",
                            startdato = LocalDate.now(),
                            opplysningspliktigOrgnummer = "232",
                            sluttdato = null,
                        ),
                    ),
            )
        sporsmal.sporsmalstekst `should be equal to` "Har du andre inntektskilder enn Politiet, Brannvesenet og Sykebilen?"
        sporsmal.metadata!!.tilAndreInntektskilderMetadata().kjenteInntektskilder `should be equal to`
            listOf(
                KjentInntektskilde(
                    navn = "Politiet",
                    kilde = Kilde.SYKMELDING,
                    orgnummer = "111",
                ),
                KjentInntektskilde(
                    navn = "Brannvesenet",
                    kilde = Kilde.INNTEKTSKOMPONENTEN,
                    orgnummer = "222",
                ),
                KjentInntektskilde(
                    navn = "Sykebilen",
                    kilde = Kilde.AAAREG,
                    orgnummer = "876",
                ),
            )
    }
}
