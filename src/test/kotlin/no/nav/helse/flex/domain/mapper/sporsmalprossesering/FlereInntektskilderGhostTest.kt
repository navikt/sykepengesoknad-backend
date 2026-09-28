package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraInntektskomponenten
import no.nav.helse.flex.soknadsopprettelse.Arbeidsforholdstype
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.sykepengesoknad.kafka.KildeDTO
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.test.util.AssertionErrors.fail
import java.time.LocalDate

class FlereInntektskilderGhostTest {
    private val testTom = LocalDate.of(2026, 9, 25)
    private val testFom = testTom.minusDays(19)
    private val sykmeldtOrgnummer = "77777777"

    private val sykmeldtArbeidsforhold =
        ArbeidsforholdFraAAreg(
            opplysningspliktigOrgnummer = "19191919191991",
            arbeidsstedOrgnummer = sykmeldtOrgnummer,
            arbeidsstedNavn = "sausefabrikken AS",
            startdato = testFom,
            sluttdato = testFom,
        )

    private val sykmeldtInntektskomponenten =
        ArbeidsforholdFraInntektskomponenten(
            orgnummer = sykmeldtOrgnummer,
            navn = "sausefabrikken AS",
            arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER,
        )

    private val ghostInntekt =
        ArbeidsforholdFraInntektskomponenten(
            orgnummer = "3233626262617",
            navn = "flausefabrikken AS",
            arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER,
        )

    private val ghostInntekt2 =
        ArbeidsforholdFraInntektskomponenten(
            orgnummer = "4424146278",
            navn = "Tausefabrikken AS",
            arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER,
        )
    private val ghostInntekt3 =
        ArbeidsforholdFraInntektskomponenten(
            orgnummer = "782382921",
            navn = "Rausefabrikken AS",
            arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER,
        )

    @Test
    fun `Flere ghost inntektskilder blir mappet riktig`() {
        val resultat =
            hentFlereInntektskilderGhost(
                fom = testFom,
                tom = testTom,
                arbeidsforholdFraAareg = listOf(sykmeldtArbeidsforhold),
                inntektsforholdFraInntektskomponenten =
                    listOf(
                        sykmeldtInntektskomponenten,
                        ghostInntekt,
                        ghostInntekt2,
                        ghostInntekt3,
                    ),
                arbeidsgiverOrgnummer = sykmeldtOrgnummer,
            )

        resultat.size `should be equal to` 3

        for (inntekt in resultat) {
            when (inntekt.navn) {
                "flausefabrikken AS" -> {
                    inntekt.orgnummer `should be equal to` "3233626262617"
                    inntekt.kilde `should be equal to` KildeDTO.INNTEKTSKOMPONENTEN
                }
                "Tausefabrikken AS" -> {
                    inntekt.orgnummer `should be equal to` "4424146278"
                    inntekt.kilde `should be equal to` KildeDTO.INNTEKTSKOMPONENTEN
                }
                "Rausefabrikken AS" -> {
                    inntekt.orgnummer `should be equal to` "782382921"
                    inntekt.kilde `should be equal to` KildeDTO.INNTEKTSKOMPONENTEN
                }
                else -> fail("Uventet inntektskilde: ${inntekt.navn}")
            }
        }
    }

    @Test
    fun `Arbeidsforhold med startdato etter skjæringstidspunkt filtreres bort`() {
        val tilkommenArbeidsforhold =
            ArbeidsforholdFraAAreg(
                opplysningspliktigOrgnummer = "232309191",
                arbeidsstedOrgnummer = "312321312",
                arbeidsstedNavn = "pausefabrikken AS",
                startdato = testTom.minusDays(1),
                sluttdato = null,
            )

        val resultat =
            hentFlereInntektskilderGhost(
                fom = testFom,
                tom = testTom,
                arbeidsforholdFraAareg = listOf(sykmeldtArbeidsforhold, tilkommenArbeidsforhold),
                inntektsforholdFraInntektskomponenten =
                    listOf(
                        sykmeldtInntektskomponenten,
                        ghostInntekt,
                        ArbeidsforholdFraInntektskomponenten(
                            orgnummer = "312321312",
                            navn = "pausefabrikken AS",
                            arbeidsforholdstype = Arbeidsforholdstype.ARBEIDSTAKER,
                        ),
                    ),
                arbeidsgiverOrgnummer = sykmeldtOrgnummer,
            )

        resultat.size `should be equal to` 1
        resultat.first().navn `should be equal to` "flausefabrikken AS"
        resultat.first().kilde `should be equal to` KildeDTO.INNTEKTSKOMPONENTEN
        resultat.first().orgnummer `should be equal to` "3233626262617"
    }

    @Test
    fun `Arbeidsforhold oppstart før skjæringstidspunktet og ikke har inntekt i inntektskomponenten filtreres bort`() {
        val nyttArbeidsforhold =
            ArbeidsforholdFraAAreg(
                opplysningspliktigOrgnummer = "232309191",
                arbeidsstedOrgnummer = "312321312",
                arbeidsstedNavn = "pausefabrikken AS",
                startdato = testFom.minusDays(10),
                sluttdato = null,
            )

        val resultat =
            hentFlereInntektskilderGhost(
                fom = testFom,
                tom = testTom,
                arbeidsforholdFraAareg = listOf(sykmeldtArbeidsforhold, nyttArbeidsforhold),
                inntektsforholdFraInntektskomponenten = listOf(sykmeldtInntektskomponenten, ghostInntekt),
                arbeidsgiverOrgnummer = sykmeldtOrgnummer,
            )

        resultat.size `should be equal to` 1
        resultat.first().navn `should be equal to` "flausefabrikken AS"
        resultat.first().kilde `should be equal to` KildeDTO.INNTEKTSKOMPONENTEN
        resultat.first().orgnummer `should be equal to` "3233626262617"
    }

    @Test
    fun `Ghostinntekt finnes selv om ingen arbeidsforhold`() {
        val resultat =
            hentFlereInntektskilderGhost(
                fom = testFom,
                tom = testTom,
                arbeidsforholdFraAareg = emptyList(),
                inntektsforholdFraInntektskomponenten = listOf(ghostInntekt, ghostInntekt2, ghostInntekt3),
                arbeidsgiverOrgnummer = null,
            )

        resultat.size `should be equal to` 3
        for (inntekt in resultat) {
            when (inntekt.navn) {
                "flausefabrikken AS" -> {
                    inntekt.orgnummer `should be equal to` "3233626262617"
                    inntekt.kilde `should be equal to` KildeDTO.INNTEKTSKOMPONENTEN
                }
                "Tausefabrikken AS" -> {
                    inntekt.orgnummer `should be equal to` "4424146278"
                    inntekt.kilde `should be equal to` KildeDTO.INNTEKTSKOMPONENTEN
                }
                "Rausefabrikken AS" -> {
                    inntekt.orgnummer `should be equal to` "782382921"
                    inntekt.kilde `should be equal to` KildeDTO.INNTEKTSKOMPONENTEN
                }
                else -> fail("Uventet inntektskilde: ${inntekt.navn}")
            }
        }
    }
}
