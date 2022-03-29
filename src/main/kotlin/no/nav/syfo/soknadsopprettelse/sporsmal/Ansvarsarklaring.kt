package no.nav.syfo.soknadsopprettelse.sporsmal

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svartype
import no.nav.syfo.soknadsopprettelse.ANSVARSERKLARING

fun ansvarserklaringSporsmal(reisetilskudd: Boolean = false): Sporsmal {

    val sporsmalstekst = if (reisetilskudd) {
        "Jeg vet at jeg kan miste retten til reisetilskudd og sykepenger hvis opplysningene jeg gir ikke er riktige eller fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil opplysninger kan være straffbart."
    } else {
        "Jeg vet at jeg kan miste retten til sykepenger hvis opplysningene jeg gir ikke er riktige eller fullstendige. Jeg vet også at NAV kan holde igjen eller kreve tilbake penger, og at å gi feil opplysninger kan være straffbart."
    }
    return Sporsmal(
        tag = ANSVARSERKLARING,
        sporsmalstekst = sporsmalstekst,
        svartype = Svartype.CHECKBOX_PANEL
    )
}
