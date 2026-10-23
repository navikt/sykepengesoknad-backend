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
    tilkomneArbeidsforholdAareg: List<ArbeidsforholdFraAAreg>,
    arbeidsgiverOrgnummerSoknad: String?,
): List<KjentInntektskilde> {
    val tilkomneOrgnumre = tilkomneArbeidsforholdAareg.map { it.arbeidsstedOrgnummer }

    return arbeidsforholdFraInntektskomponenten
        .map { KjentInntektskilde(navn = it.navn, kilde = Kilde.INNTEKTSKOMPONENTEN, orgnummer = it.orgnummer) }
        .distinct()
        .filterNot { it.orgnummer == arbeidsgiverOrgnummerSoknad }
        .filterNot { it.orgnummer in tilkomneOrgnumre }
}
