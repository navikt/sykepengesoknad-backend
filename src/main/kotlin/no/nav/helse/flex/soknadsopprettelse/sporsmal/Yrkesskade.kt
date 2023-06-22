package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.util.DatoUtil.formatterDato
import no.nav.helse.flex.yrkesskade.YrkesskadeSak
import no.nav.helse.flex.yrkesskade.YrkesskadeSporsmalGrunnlag

fun YrkesskadeSporsmalGrunnlag.yrkeskadeSporsmal(): List<Sporsmal> {
    if (v2Enabled) {
        if (v2GodkjenteSaker.isEmpty()) {
            return emptyList()
        }
        return listOf(yrkesskadeSporsmalV2(v2GodkjenteSaker))
    }
    if (v1Indikator) {
        return listOf(yrkesskadeSporsmalV1())
    }
    return emptyList()
}

private fun yrkesskadeSporsmalV1(): Sporsmal =
    Sporsmal(
        tag = YRKESSKADE,
        sporsmalstekst = "Er du sykmeldt på grunn av en yrkesskade eller yrkessykdom?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = YRKESSKADE_SAMMENHENG,
                sporsmalstekst = "Er det en sammenheng mellom dette sykefraværet og tidligere yrkesskade?",
                svartype = Svartype.JA_NEI
            )
        )
    )

fun skapSkadedatoTekst(y: YrkesskadeSak): String {
    if (y.skadedato == null) {
        return "Vedtaksdato ${formatterDato(y.vedtaksdato)}"
    }

    return "Skadedato ${formatterDato(y.skadedato)} (Vedtaksdato ${formatterDato(y.vedtaksdato)})"
}

private fun yrkesskadeSporsmalV2(v2GodkjenteSaker: List<YrkesskadeSak>): Sporsmal =
    Sporsmal(
        tag = YRKESSKADE_V2,
        sporsmalstekst = "Skyldes dette sykefraværet en eller flere av disse godkjente yrkesskadene?",
        svartype = Svartype.JA_NEI,
        kriterieForVisningAvUndersporsmal = Visningskriterie.JA,
        undersporsmal = listOf(
            Sporsmal(
                tag = YRKESSKADE_V2_VELG_DATO,
                sporsmalstekst = "Velg hvilken skadedato dette sykefraværet har sammenheng med",
                svartype = Svartype.CHECKBOX_GRUPPE,
                undersporsmal = v2GodkjenteSaker.map {
                    Sporsmal(
                        sporsmalstekst = skapSkadedatoTekst(it),
                        svartype = Svartype.CHECKBOX,
                        tag = YRKESSKADE_V2_DATO
                    )
                }
            )
        )
    )
