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

private fun Sporsmal.sorterUndersporsmal(): Sporsmal {
    val sorterteUndersporsmal = this.undersporsmal
        .map { it.sorterUndersporsmal() }
        .sortedBy {
            when (this.tag) {
                HVILKE_ANDRE_INNTEKTSKILDER -> it.sorteringAndreInntektskilder()
                ARBEIDSGIVER -> it.sorteringArbeidsgiver()
                REISE_MED_BIL -> it.sorteringReiseMedBil()
                else -> it.tag
            }
        }
    return this.copy(undersporsmal = sorterteUndersporsmal)
}

private fun Sporsmal.sorteringAndreInntektskilder(): String {
    return when (tag) {
        INNTEKTSKILDE_ANDRE_ARBEIDSFORHOLD -> "0"
        INNTEKTSKILDE_ARBEIDSFORHOLD -> "1"
        INNTEKTSKILDE_SELVSTENDIG -> "2"
        INNTEKTSKILDE_SELVSTENDIG_DAGMAMMA -> "3"
        INNTEKTSKILDE_JORDBRUKER -> "4"
        INNTEKTSKILDE_FRILANSER -> "5"
        INNTEKTSKILDE_OMSORGSLONN -> "6"
        INNTEKTSKILDE_FOSTERHJEM -> "7"
        INNTEKTSKILDE_FRILANSER_SELVSTENDIG -> "8"
        INNTEKTSKILDE_ANNET -> "9"
        else -> throw RuntimeException("Ukjent underspørsmål for andre inntektskilder: $tag")
    }
}

private fun Sporsmal.sorteringArbeidsgiver(): String {
    return when (tag) {
        SYKMELDINGSGRAD -> "0"
        FERIE -> "1"
        else -> throw RuntimeException("Ukjent underspørsmål for arbeidsgiver: $tag")
    }
}
private fun Sporsmal.sorteringReiseMedBil(): String {
    return when (tag) {
        BIL_DATOER -> "0"
        BIL_BOMPENGER -> "1"
        KM_HJEM_JOBB -> "2"
        else -> throw RuntimeException("Ukjent underspørsmål for reise med bil: $tag")
    }
}
