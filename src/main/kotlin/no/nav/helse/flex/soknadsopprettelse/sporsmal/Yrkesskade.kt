package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.soknadsopprettelse.YRKESSKADE
import no.nav.helse.flex.soknadsopprettelse.YRKESSKADE_SAMMENHENG

fun yrkesskadeSporsmal(): Sporsmal =
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
