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

fun sjekkForGhostInntekter(
    arbeidsforholdFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>,
    arbeidforholdOversiktAareg: List<ArbeidsforholdFraAAreg>,
    arbeidsgiverOrgnummerSoknad: String?,
): List<KjentInntektskilde> {
    val andreInntekter =
        arbeidsforholdFraInntektskomponenten.map { inntekt ->
            KjentInntektskilde(
                navn = inntekt.navn,
                kilde = Kilde.INNTEKTSKOMPONENTEN,
                orgnummer = inntekt.orgnummer,
            )
        }

    val tilkomneInntekterOrgNummer = arbeidforholdOversiktAareg.map { it.arbeidsstedOrgnummer }

    return andreInntekter
        .toSet()
        .filterNot { it.orgnummer == arbeidsgiverOrgnummerSoknad }
        .filterNot { it.orgnummer in tilkomneInntekterOrgNummer }
}
