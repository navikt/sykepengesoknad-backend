package no.nav.helse.flex.testutil

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar

fun Sporsmal.byttSvar(
    tag: String? = null,
    svar: String,
): Sporsmal = this.byttSvar(tag, listOf(svar))

fun Sporsmal.byttSvar(
    tag: String? = null,
    svar: List<String>,
): Sporsmal = listOf(this).byttSvar(tag ?: this.tag, svar.toList().map { Svar(null, verdi = it) }).first()

fun List<Sporsmal>.byttSvar(
    tag: String,
    svar: List<Svar>,
): List<Sporsmal> =
    map { spm ->
        when {
            spm.tag == tag -> spm.copy(svar = svar)
            spm.undersporsmal.isNotEmpty() ->
                spm.copy(
                    undersporsmal =
                        spm.undersporsmal.byttSvar(
                            tag,
                            svar,
                        ),
                )
            else -> spm
        }
    }
