package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING

fun ansvarserklaringSporsmal(reisetilskudd: Boolean = false): Sporsmal {

    val sporsmalstekst = if (reisetilskudd) {
        "Jeg bekrefter at jeg vil gi så riktige og fullstendige opplysninger som mulig."
    } else {
        "Jeg bekrefter at jeg vil gi så riktige og fullstendige opplysninger som mulig."
    }
    return Sporsmal(
        tag = ANSVARSERKLARING,
        sporsmalstekst = sporsmalstekst,
        svartype = Svartype.CHECKBOX_PANEL
    )
}
