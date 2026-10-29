package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.sporsmal.Kilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.KjentInntektskilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.jobbetDu100ProsentArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.sporsmal.jobbetDuGradertArbeidstaker

fun jobbetDuIPeriodenSporsmal(
    soknadsperioder: List<Soknadsperiode>,
    arbeidsgiverNavn: String?,
): List<Sporsmal> =
    soknadsperioder
        .lastIndex
        .downTo(0)
        .reversed()
        .map { index ->
            val periode = soknadsperioder[index]
            if (periode.grad == 100) {
                jobbetDu100ProsentArbeidstaker(periode, arbeidsgiverNavn, index)
            } else {
                jobbetDuGradertArbeidstaker(periode, arbeidsgiverNavn, index)
            }
        }

fun finnGhostInntekter(
    arbeidsforholdFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>,
    arbeidsforholdFraAareg: List<ArbeidsforholdFraAAreg>,
    arbeidsgiverOrgnummerSoknad: String?,
    erArbeidstaker: Boolean,
): List<KjentInntektskilde> {
    val arbeidsforholdAareg =
        arbeidsforholdFraAareg
            .map { KjentInntektskilde(navn = it.arbeidsstedNavn, kilde = Kilde.AAAREG, orgnummer = it.arbeidsstedOrgnummer) }
            .distinct()

    val arbeidsforholdInntekskomponenten =
        arbeidsforholdFraInntektskomponenten
            .map { KjentInntektskilde(navn = it.navn, kilde = Kilde.INNTEKTSKOMPONENTEN, orgnummer = it.orgnummer) }
            .distinct()
            .filterNot { arbeidsforhold -> arbeidsforhold.orgnummer in arbeidsforholdAareg.map { it.orgnummer } }

    // Arbeidstakere får eget spørsmål om nye arbeidsforhold (NYTT_ARBEIDSFORHOLD_UNDERVEIS), så tilkomne holdes utenfor
    return if (erArbeidstaker) {
        arbeidsforholdInntekskomponenten
            .filterNot { it.orgnummer == arbeidsgiverOrgnummerSoknad }
    } else {
        (arbeidsforholdAareg + arbeidsforholdInntekskomponenten)
            .filterNot { it.orgnummer == arbeidsgiverOrgnummerSoknad }
    }
}
