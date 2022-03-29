package no.nav.syfo.testutil

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svar

fun Sporsmal.byttSvar(tag: String? = null, svar: String): Sporsmal =
    this.byttSvar(tag, listOf(svar))

fun Sporsmal.byttSvar(tag: String? = null, svar: List<String>): Sporsmal =
    listOf(this).byttSvar(tag ?: this.tag, svar.toList().map { Svar(null, verdi = it) }).first()

private fun List<Sporsmal>.byttSvar(tag: String, svar: List<Svar>): List<Sporsmal> {
    return map { spm ->
        when {
            spm.tag == tag -> spm.copy(svar = svar)
            spm.undersporsmal.isNotEmpty() -> spm.copy(
                undersporsmal = spm.undersporsmal.byttSvar(
                    tag,
                    svar
                )
            )
            else -> spm
        }
    }
}
