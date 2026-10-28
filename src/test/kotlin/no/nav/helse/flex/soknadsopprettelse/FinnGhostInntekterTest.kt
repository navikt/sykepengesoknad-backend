package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.sporsmal.Kilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.KjentInntektskilde
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate

class FinnGhostInntekterTest {
    private val soknadsOrgnummer = "111111111"
    private val inntektskomponentenOrgnummer = "222222222"
    private val aaregOrgnummer = "333333333"

    private fun inntektskomponenten(
        orgnummer: String,
        navn: String = "Inntektskomponenten $orgnummer",
        type: Arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER,
    ) = ArbeidsforholdFraInntektskomponenten(orgnummer = orgnummer, navn = navn, arbeidsforholdstype = type)

    private fun aareg(
        orgnummer: String,
        navn: String = "Aareg $orgnummer",
    ) = ArbeidsforholdFraAAreg(
        opplysningspliktigOrgnummer = "999999999",
        arbeidsstedOrgnummer = orgnummer,
        arbeidsstedNavn = navn,
        startdato = LocalDate.of(2025, 1, 10),
        sluttdato = null,
    )

    private fun kjentFraInntektskomponenten(orgnummer: String) =
        KjentInntektskilde(
            navn = "Inntektskomponenten $orgnummer",
            kilde = Kilde.INNTEKTSKOMPONENTEN,
            orgnummer = orgnummer,
        )

    private fun kjentFraAareg(orgnummer: String) =
        KjentInntektskilde(navn = "Aareg $orgnummer", kilde = Kilde.AAAREG, orgnummer = orgnummer)

    @Nested
    inner class Arbeidstaker {
        private fun finn(
            inntektskomponenten: List<ArbeidsforholdFraInntektskomponenten> = emptyList(),
            aareg: List<ArbeidsforholdFraAAreg> = emptyList(),
            soknadsOrgnummer: String? = this@FinnGhostInntekterTest.soknadsOrgnummer,
        ) = finnGhostInntekter(
            arbeidsforholdFraInntektskomponenten = inntektskomponenten,
            tilkomneArbeidsforholdAareg = aareg,
            arbeidsgiverOrgnummerSoknad = soknadsOrgnummer,
            erArbeidstaker = true,
        )

        @Test
        fun `ingen inntekter gir ingen ghost`() {
            finn().shouldBeEmpty()
        }

        @Test
        fun `inntekt fra inntektskomponenten hos andre enn arbeidsgiver er ghost`() {
            finn(inntektskomponenten = listOf(inntektskomponenten(inntektskomponentenOrgnummer))) `should be equal to`
                listOf(kjentFraInntektskomponenten(inntektskomponentenOrgnummer))
        }

        @Test
        fun `inntekt hos søknadens arbeidsgiver er ikke ghost`() {
            finn(inntektskomponenten = listOf(inntektskomponenten(soknadsOrgnummer))).shouldBeEmpty()
        }

        @Test
        fun `samme orgnummer flere ganger gir én ghost`() {
            finn(
                inntektskomponenten =
                    listOf(
                        inntektskomponenten(inntektskomponentenOrgnummer),
                        inntektskomponenten(inntektskomponentenOrgnummer),
                    ),
            ) `should be equal to` listOf(kjentFraInntektskomponenten(inntektskomponentenOrgnummer))
        }

        @Test
        fun `tilkommet arbeidsforhold filtreres bort fra ghost siden det har eget spørsmål`() {
            finn(
                inntektskomponenten = listOf(inntektskomponenten(aaregOrgnummer)),
                aareg = listOf(aareg(aaregOrgnummer)),
            ).shouldBeEmpty()
        }

        @Test
        fun `arbeidsforhold som bare finnes i Aareg blir ikke ghost for arbeidstaker`() {
            finn(aareg = listOf(aareg(aaregOrgnummer))).shouldBeEmpty()
        }

        @Test
        fun `bare ikke-tilkomne inntekter blir igjen`() {
            finn(
                inntektskomponenten =
                    listOf(
                        inntektskomponenten(inntektskomponentenOrgnummer),
                        inntektskomponenten(aaregOrgnummer),
                    ),
                aareg = listOf(aareg(aaregOrgnummer)),
            ) `should be equal to` listOf(kjentFraInntektskomponenten(inntektskomponentenOrgnummer))
        }
    }

    @Nested
    inner class SelvstendigOgFrilanser {
        private fun finn(
            inntektskomponenten: List<ArbeidsforholdFraInntektskomponenten> = emptyList(),
            aareg: List<ArbeidsforholdFraAAreg> = emptyList(),
            soknadsOrgnummer: String? = null,
        ) = finnGhostInntekter(
            arbeidsforholdFraInntektskomponenten = inntektskomponenten,
            tilkomneArbeidsforholdAareg = aareg,
            arbeidsgiverOrgnummerSoknad = soknadsOrgnummer,
            erArbeidstaker = false,
        )

        @Test
        fun `ingen inntekter gir ingen ghost`() {
            finn().shouldBeEmpty()
        }

        @Test
        fun `inntekt fra inntektskomponenten er ghost uten arbeidsgiver i søknaden`() {
            finn(
                inntektskomponenten = listOf(inntektskomponenten(inntektskomponentenOrgnummer, type = Arbeidsforholdstype.FRILANSER)),
            ) `should be equal to` listOf(kjentFraInntektskomponenten(inntektskomponentenOrgnummer))
        }

        @Test
        fun `arbeidsforhold som bare finnes i Aareg er ghost med kilde AAAREG`() {
            finn(aareg = listOf(aareg(aaregOrgnummer))) `should be equal to` listOf(kjentFraAareg(aaregOrgnummer))
        }

        @Test
        fun `tilkommet arbeidsforhold beholdes når det også finnes i inntektskomponenten, uten duplikat`() {
            finn(
                inntektskomponenten = listOf(inntektskomponenten(aaregOrgnummer)),
                aareg = listOf(aareg(aaregOrgnummer)),
            ) `should be equal to` listOf(kjentFraInntektskomponenten(aaregOrgnummer))
        }

        @Test
        fun `inntektskomponenten kommer før Aareg i resultatet`() {
            finn(
                inntektskomponenten = listOf(inntektskomponenten(inntektskomponentenOrgnummer)),
                aareg = listOf(aareg(aaregOrgnummer)),
            ) `should be equal to`
                listOf(
                    kjentFraInntektskomponenten(inntektskomponentenOrgnummer),
                    kjentFraAareg(aaregOrgnummer),
                )
        }

        @Test
        fun `samme orgnummer flere ganger i Aareg gir én ghost`() {
            finn(aareg = listOf(aareg(aaregOrgnummer), aareg(aaregOrgnummer))) `should be equal to`
                listOf(kjentFraAareg(aaregOrgnummer))
        }

        @Test
        fun `søknadens egen arbeidsgiver filtreres bort fra både inntektskomponenten og Aareg`() {
            finn(
                inntektskomponenten = listOf(inntektskomponenten(soknadsOrgnummer)),
                aareg = listOf(aareg(soknadsOrgnummer)),
                soknadsOrgnummer = soknadsOrgnummer,
            ).shouldBeEmpty()
        }

        @Test
        fun `null som arbeidsgiver i søknaden filtrerer ikke bort noe`() {
            finn(
                inntektskomponenten = listOf(inntektskomponenten(inntektskomponentenOrgnummer)),
                aareg = listOf(aareg(aaregOrgnummer)),
                soknadsOrgnummer = null,
            ).map { it.orgnummer } `should be equal to` listOf(inntektskomponentenOrgnummer, aaregOrgnummer)
        }
    }
}
