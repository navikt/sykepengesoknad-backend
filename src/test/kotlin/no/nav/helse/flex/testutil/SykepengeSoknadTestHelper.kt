package no.nav.helse.flex.testutil

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad

fun Sykepengesoknad.besvarsporsmal(
    tag: String,
    svar: String? = null,
    svarListe: List<String>? = null,
): Sykepengesoknad {
    if (svar == null && svarListe == null) {
        throw RuntimeException("Svar og svar liste kan ikke være null, hvis du vil fjerne svar, bruk tom liste")
    }
    val sporsmal =
        this.alleSporsmalOgUndersporsmal().find { it.tag == tag }
            ?: throw RuntimeException("Spørsmål ikke funnet, $tag")

    val sporsmalSvar = svarListe?.map { Svar(null, verdi = it) } ?: listOf(Svar(null, verdi = svar!!))

    return this.byttSvar(sporsmal, sporsmalSvar)
}

fun copyByttSvar(
    sykepengesoknad: Sykepengesoknad,
    sporsmal: Sporsmal,
    svar: List<Svar>,
): Sykepengesoknad {
    var alleSporsmal = sykepengesoknad.sporsmal
    alleSporsmal = byttSvar(alleSporsmal, sporsmal, svar)
    return sykepengesoknad.copy(sporsmal = alleSporsmal)
}

fun Sykepengesoknad.byttSvar(
    sporsmal: Sporsmal,
    svar: List<Svar>,
): Sykepengesoknad = copyByttSvar(this, sporsmal, svar)

private fun byttSvar(
    alleSporsmal: List<Sporsmal>,
    sporsmal: Sporsmal,
    svar: List<Svar>,
): List<Sporsmal> =
    alleSporsmal.map { spm ->
        when {
            spm.tag == sporsmal.tag -> sporsmal.copy(svar = svar)
            spm.undersporsmal.isNotEmpty() -> spm.copy(undersporsmal = byttSvar(spm.undersporsmal, sporsmal, svar))
            else -> spm
        }
    }
