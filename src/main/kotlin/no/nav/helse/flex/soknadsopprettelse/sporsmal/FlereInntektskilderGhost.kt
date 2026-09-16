@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.util.formatterPeriode
import no.nav.helse.flex.util.toJsonNode

fun flereInntektskilderGhost(
    sykmeldingOrgnavn: String,
    sykmeldingOrgnr: String,
    andreKjenteArbeidsforholdFraInntektskomponenten: List<ArbeidsforholdFraInntektskomponenten>,
    nyeArbeidsforholdFraAareg: List<ArbeidsforholdFraAAreg>?,
    soknadsperiode: Soknadsperiode,
): Sporsmal {
    val alleArbeidsforhold = mutableListOf(KjentInntektskilde(sykmeldingOrgnavn, Kilde.SYKMELDING, sykmeldingOrgnr))
    alleArbeidsforhold.addAll(
        andreKjenteArbeidsforholdFraInntektskomponenten.map {
            KjentInntektskilde(
                it.navn,
                Kilde.INNTEKTSKOMPONENTEN,
                it.orgnummer,
            )
        },
    )
    nyeArbeidsforholdFraAareg
        ?.filter { arbeidsforhold ->
            !alleArbeidsforhold.map { it.orgnummer }.contains(arbeidsforhold.arbeidsstedOrgnummer)
        }?.forEach {
            alleArbeidsforhold.add(KjentInntektskilde(it.arbeidsstedNavn, Kilde.AAAREG, it.arbeidsstedOrgnummer))
        }

    fun skapSporsmal(periode: Soknadsperiode): String =
        "Har du jobbet noe mer i disse enn du vanligvis gjør, mens du var sykmeldt i perioden " +
            "${formatterPeriode(periode.fom, periode.tom)}?"

    return Sporsmal(
        tag = FLERE_INNTEKTSKILDER_GHOST,
        sporsmalstekst = skapSporsmal(soknadsperiode),
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        metadata =
            AndreInntektskilderMetadata(
                kjenteInntektskilder = alleArbeidsforhold,
            ).toJsonNode(),
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = JOBBET_MER_I,
                    sporsmalstekst = "Hvilke jobbet du mer i?",
                    undertekst = "Du kan velge en eller flere.",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        alleArbeidsforhold.mapIndexed { index, inntektskilde ->
                            Sporsmal(
                                tag = JOBBET_MER_I_VALG + index,
                                sporsmalstekst = inntektskilde.navn,
                                svartype = Svartype.CHECKBOX,
                            )
                        },
                ),
                andreInntektskilderArbeidstakerV2(
                    sykmeldingOrgnavn = sykmeldingOrgnavn,
                    sykmeldingOrgnr = sykmeldingOrgnr,
                    andreKjenteArbeidsforholdFraInntektskomponenten = andreKjenteArbeidsforholdFraInntektskomponenten,
                    nyeArbeidsforholdFraAareg = nyeArbeidsforholdFraAareg,
                ),
            ),
    )
}
