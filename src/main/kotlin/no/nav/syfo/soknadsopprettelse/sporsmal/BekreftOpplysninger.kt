package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.soknadsopprettelse.BEKREFT_OPPLYSNINGER

fun bekreftOpplysningerSporsmal(): Sporsmal {
    return Sporsmal(
        tag = BEKREFT_OPPLYSNINGER,
        sporsmalstekst = "Jeg har lest all informasjonen jeg har fått i søknaden og bekrefter at opplysningene jeg har gitt er korrekte.",
        svartype = Svartype.CHECKBOX_PANEL
    )
}
