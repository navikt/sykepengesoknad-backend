package no.nav.helse.flex.oppdatersporsmal.soknad

import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.flatten

fun Sykepengesoknad.erAvType(vararg typer: Soknadstype): Boolean {
    typer.forEach {
        if (it == this.soknadstype) {
            return true
        }
    }
    return false
}

fun Sykepengesoknad.erIkkeAvType(vararg typer: Soknadstype): Boolean = !erAvType(*typer)

fun Sykepengesoknad.leggTilSporsmaal(sporsmal: Sporsmal): Sykepengesoknad {
    val eksisterendeSpm = this.sporsmal.find { it.tag == sporsmal.tag }
    if (eksisterendeSpm != null) {
        return if (eksisterendeSpm.erUlikUtenomSvarTekstOgId(sporsmal)) {
            // Fjerne spørsmpålet
            this.copy(
                sporsmal = this.sporsmal.filterNot { it.tag == sporsmal.tag } + sporsmal,
            )
        } else {
            this
        }
    }

    return this.copy(sporsmal = this.sporsmal + sporsmal)
}

fun Sykepengesoknad.leggTilSporsmaal(sporsmal: List<Sporsmal>): Sykepengesoknad {
    var soknad = this
    sporsmal.forEach {
        soknad = soknad.leggTilSporsmaal(it)
    }
    return soknad
}

fun List<Sporsmal>.erUlikUtenomSvar(sammenlign: List<Sporsmal>): Boolean {
    fun List<Sporsmal>.flattenOgFjernSvar(): List<Sporsmal> {
        return this.flatten().map { it.copy(svar = emptyList(), undersporsmal = emptyList(), metadata = null) }.sortedBy { it.id }
    }

    return this.flattenOgFjernSvar() != sammenlign.flattenOgFjernSvar()
}

fun List<Sporsmal>.erUlikUtenomSvarTekstOgId(sammenlign: List<Sporsmal>): Boolean {
    fun List<Sporsmal>.flattenOgFjernSvarOgId(): Set<Sporsmal> {
        return this.flatten().map { it.copy(svar = emptyList(), undersporsmal = emptyList(), sporsmalstekst = null, metadata = null) }
            .map { it.copy(id = null) }.toSet()
    }

    val a = this.flattenOgFjernSvarOgId()
    val b = sammenlign.flattenOgFjernSvarOgId()
    return a != b
}

fun Sporsmal.erUlikUtenomSvarTekstOgId(sammenlign: Sporsmal): Boolean = listOf(this).erUlikUtenomSvarTekstOgId(listOf(sammenlign))
