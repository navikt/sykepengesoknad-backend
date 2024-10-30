package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING

fun ansvarserklaringSporsmal(): Sporsmal {
    val sporsmalstekst = "Jeg bekrefter at jeg vil svare så riktig som jeg kan."
    return Sporsmal(
        tag = ANSVARSERKLARING,
        sporsmalstekst = sporsmalstekst,
        svartype = Svartype.CHECKBOX_PANEL,
    )
}
