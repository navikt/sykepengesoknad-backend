package no.nav.helse.flex.oppdatersporsmal.soknad

import no.nav.helse.flex.domain.Sporsmal

fun Sporsmal.nullstillTidligereSvar(): Sporsmal {
    return if (kriterieForVisningAvUndersporsmal == null || kriterieForVisningAvUndersporsmal.name == forsteSvar) {
        copy(undersporsmal = undersporsmal.map { it.nullstillTidligereSvar() })
    } else {
        copy(undersporsmal = undersporsmal.nullstillUndersporsmalSvar())
    }
}

private fun List<Sporsmal>.nullstillUndersporsmalSvar(): List<Sporsmal> {
    return map {
        it.copy(
            svar = emptyList(),
            undersporsmal = it.undersporsmal.nullstillUndersporsmalSvar()
        )
    }
}
