@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.formatterPeriode
import no.nav.helse.flex.util.toJsonNode

fun flereInntektskilderGhost(
    andreKjenteInntektskilder: List<KjentInntektskilde>,
    soknadsperiode: Soknadsperiode,
): Sporsmal {
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
                kjenteInntektskilder = andreKjenteInntektskilder,
            ).toJsonNode(),
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = JOBBET_MER_I,
                    sporsmalstekst = "Hvilke jobbet du mer i?",
                    undertekst = "Du kan velge en eller flere.",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        andreKjenteInntektskilder.mapIndexed { index, arbeidsforhold ->
                            Sporsmal(
                                tag = JOBBET_MER_I_VALG + index,
                                sporsmalstekst = arbeidsforhold.navn,
                                svartype = Svartype.CHECKBOX,
                            )
                        },
                ),
                andreInntektskilderArbeidstakerV2(),
            ),
    )
}
