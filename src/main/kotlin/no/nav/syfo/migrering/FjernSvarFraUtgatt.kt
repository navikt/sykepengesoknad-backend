package no.nav.syfo.migrering

import no.nav.syfo.domain.Soknadstatus.UTGATT
import no.nav.syfo.domain.Sykepengesoknad

fun Sykepengesoknad.fjernSvarFraUtgatt(): Sykepengesoknad {
    var soknaden = this
    if (status == UTGATT) {
        val sporsmal = this.alleSporsmalOgUndersporsmal()
        sporsmal.forEach {
            soknaden = soknaden.replaceSporsmal(it.copy(svar = emptyList()))
        }
        return soknaden
    }
    return soknaden
}
