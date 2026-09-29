package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.soknadsopprettelse.sporsmal.Kilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.KjentInntektskilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.jobbetDu100ProsentArbeidstaker
import no.nav.helse.flex.soknadsopprettelse.sporsmal.jobbetDuGradertArbeidstaker

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

fun sjekkForGhostInntekter(
    arbeidsforholdFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>,
    arbeidforholdOversiktAareg: List<ArbeidsforholdFraAAreg>,
    arbeidsgiverOrgnummerSoknad: String?,
    eksisterendeSoknader: List<Sykepengesoknad>,
): List<KjentInntektskilde> {

    val andreInntekter = arbeidsforholdFraInntektskomponenten.map { inntekt ->
        KjentInntektskilde(
            navn = inntekt.navn,
            kilde = Kilde.INNTEKTSKOMPONENTEN,
            orgnummer = inntekt.orgnummer,
        )
    }

    val tilkomneInntekter =
        arbeidforholdOversiktAareg.map { arbeidsforhold ->
            KjentInntektskilde(
                navn = arbeidsforhold.arbeidsstedNavn,
                kilde = Kilde.AAAREG,
                orgnummer = arbeidsforhold.arbeidsstedOrgnummer,
            )
        }

    eksisterendeSoknader
        .filter { it.fom != null && it.tom != null }

    val ghostInntekter = andreInntekter.toSet().filterNot { it.orgnummer == arbeidsgiverOrgnummerSoknad }
            .filterNot {it.orgnummer in eksisterendeSoknader.map {it.arbeidsgiverOrgnummer}}
            .filterNot { it.orgnummer in tilkomneInntekter.map { it.orgnummer } }

    return ghostInntekter.toList()
}
