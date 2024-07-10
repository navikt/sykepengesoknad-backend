package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil.formatterDato
import no.nav.helse.flex.yrkesskade.YrkesskadeSak
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag

fun YrkesskadeSporsmalGrunnlag.yrkeskadeSporsmal(): List<Sporsmal> {
    if (godkjenteSaker.isEmpty()) {
        return emptyList()
    }
    return listOf(yrkesskadeSporsmalV2(godkjenteSaker))
}

fun skapSkadedatoTekst(y: YrkesskadeSak): String {
    if (y.skadedato == null) {
        return "Vedtaksdato ${formatterDato(y.vedtaksdato)}"
    }

    return "Skadedato ${formatterDato(y.skadedato)} (Vedtaksdato ${formatterDato(y.vedtaksdato)})"
}

private fun yrkesskadeSporsmalV2(v2GodkjenteSaker: List<YrkesskadeSak>): Sporsmal =
    Sporsmal(
        tag = YRKESSKADE_V2,
        sporsmalstekst = "Skyldes dette sykefraværet en yrkesskade?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal =
            listOf(
                Sporsmal(
                    tag = YRKESSKADE_V2_VELG_DATO,
                    sporsmalstekst = "Hvilken skadedato skyldes dette sykefraværet? Du kan velge flere",
                    svartype = Svartype.CHECKBOX_GRUPPE,
                    undersporsmal =
                        v2GodkjenteSaker.map {
                            Sporsmal(
                                sporsmalstekst = skapSkadedatoTekst(it),
                                svartype = Svartype.CHECKBOX,
                                tag = YRKESSKADE_V2_DATO,
                                // For sortering.
                                undertekst = (it.skadedato ?: it.vedtaksdato).toString(),
                            )
                        } +
                            Sporsmal(
                                sporsmalstekst = "Nylig registrert skade",
                                svartype = Svartype.CHECKBOX,
                                tag = YRKESSKADE_V2_DATO,
                                undertekst = "zzz",
                                // todo fungerer sortering?
                            ),
                ),
            ),
    )
