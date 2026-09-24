package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.sporsmal.Kilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.KjentInntektskilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.jobbetDu100ProsentArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.sporsmal.jobbetDuGradertArbeidstaker
import no.nav.helse.flex.util.isAfterOrEqual
import no.nav.helse.flex.util.isBeforeOrEqual
import java.time.LocalDate

fun filtrerArbeidsforholdISykeforlop(
    arbeidsforholdoversiktResponse: List<ArbeidsforholdFraAAreg>?,
    fom: LocalDate,
    tom: LocalDate,
): Set<ArbeidsforholdFraAAreg>? =
    arbeidsforholdoversiktResponse
        ?.filter { it.startdato.isBeforeOrEqual(tom) }
        ?.filter {
            if (it.sluttdato == null) {
                return@filter true
            }
            val afterOrEqual = it.sluttdato.isAfterOrEqual(fom)
            return@filter afterOrEqual
        }?.toSet()

fun jobbetDuIPeriodenSporsmal(
    soknadsperioder: List<Soknadsperiode>,
    arbeidsgiverNavn: String,
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

fun sjekkNyeArbeidsforhold(
    fom: LocalDate,
    tom: LocalDate,
    arbeidforholdOversikt: List<ArbeidsforholdFraAAreg>?,
): Set<ArbeidsforholdFraAAreg>? =
    filtrerArbeidsforholdISykeforlop(
        arbeidsforholdoversiktResponse = arbeidforholdOversikt,
        fom = fom,
        tom = tom,
    )

fun sjekkGhostInntekter(
    arbeidsforholdFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>,
    arbeidforholdOversikt: List<ArbeidsforholdFraAAreg>,
    arbeidsgiverOrgnummer: String?,
    nyeArbeidsforhold: List<ArbeidsforholdFraAAreg>,
): List<KjentInntektskilde> {
    val inntekterFraInntektskomponenten =
        arbeidsforholdFraInntektskomponenten.map { inntekt ->
            KjentInntektskilde(
                navn = inntekt.navn,
                kilde = Kilde.INNTEKTSKOMPONENTEN,
                orgnummer = inntekt.orgnummer,
            )
        }

    val arbeidsforholdFraAAreg =
        arbeidforholdOversikt.map { arbeidsforhold ->
            KjentInntektskilde(
                navn = arbeidsforhold.arbeidsstedNavn,
                kilde = Kilde.AAAREG,
                orgnummer = arbeidsforhold.arbeidsstedOrgnummer,
            )
        }

    val andreKjenteInntektskilder =
        buildSet {
            addAll(inntekterFraInntektskomponenten)
            addAll(arbeidsforholdFraAAreg)
        }.filterNot { it.orgnummer == arbeidsgiverOrgnummer }
            .filterNot { kjentInntektskilde ->
                kjentInntektskilde.orgnummer in
                    nyeArbeidsforhold.map { it.arbeidsstedOrgnummer }
            }

    return andreKjenteInntektskilder.toList()
}
