package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING

fun ansvarserklaringSporsmal(): Sporsmal {
    val sporsmalstekst = "Jeg vil svare så godt jeg kan på spørsmålene i søknaden."
    return Sporsmal(
        tag = ANSVARSERKLARING,
        sporsmalstekst = sporsmalstekst,
        svartype = Svartype.CHECKBOX_PANEL,
    )
}
