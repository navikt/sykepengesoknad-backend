package no.nav.helse.flex.soknadsopprettelse.aaregdata

import no.nav.helse.flex.client.aareg.Arbeidsforhold

fun Arbeidsforhold.erIkkeVidereforingAvAnnetArbeidsforhold(alleArbeidsforhold: List<Arbeidsforhold>): Boolean {
    return !erVidereforingAvAnnetArbeidsforhold(alleArbeidsforhold)
}

private fun Arbeidsforhold.erVidereforingAvAnnetArbeidsforhold(alleArbeidsforhold: List<Arbeidsforhold>): Boolean {
    return alleArbeidsforhold
        .filter { it !== this }
        .filter { it != this }
        .any { it.erMestSannsynligEllerKanskjeVidereføringAv(this) }
}
