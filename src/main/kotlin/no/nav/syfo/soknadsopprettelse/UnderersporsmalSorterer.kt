package no.nav.syfo.soknadsopprettelse

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Sykepengesoknad

fun Sykepengesoknad.sorterUndersporsmal(): Sykepengesoknad {
    return this
        .copy(
            sporsmal = this.sporsmal.map {
                it.sorterUndersporsmal()
            }
        )
}

fun Sporsmal.sorterUndersporsmal(): Sporsmal {
    val sorterteUndersporsmal = this.undersporsmal
        .map { it.sorterUndersporsmal() }
        .sortedBy { it.tag }
    return this.copy(undersporsmal = sorterteUndersporsmal)
}
