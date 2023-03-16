package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.soknadsopprettelse.YRKESSKADE

fun yrkesskadeSporsmal(): Sporsmal =
    Sporsmal(
        tag = YRKESSKADE,
        sporsmalstekst = "Er du sykmeldt på grunn av en yrkesskade?",
        svartype = Svartype.JA_NEI
    )
