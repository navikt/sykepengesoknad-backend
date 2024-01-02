package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER

fun bekreftOpplysningerSporsmal(): Sporsmal {
    return Sporsmal(
        tag = BEKREFT_OPPLYSNINGER,
        sporsmalstekst = "Jeg har lest all informasjonen jeg har fått i søknaden og bekrefter at opplysningene jeg har gitt er korrekte.",
        svartype = Svartype.CHECKBOX_PANEL,
    )
}
