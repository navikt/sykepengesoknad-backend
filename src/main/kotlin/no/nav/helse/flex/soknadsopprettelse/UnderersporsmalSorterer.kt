package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad

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
            val tagUtenIndex = fjernIndexFraTag(this.tag)
            when (tagUtenIndex) {
                HVILKE_ANDRE_INNTEKTSKILDER -> it.sorteringAndreInntektskilder()
                INNTEKTSKILDE_SELVSTENDIG_VARIG_ENDRING_GRUPPE -> it.sorteringVarigEndring()
                ARBEIDSGIVER -> it.sorteringArbeidsgiver()
                REISE_MED_BIL -> it.sorteringReiseMedBil()
                UTENLANDSK_SYKMELDING_BOSTED -> it.sorteringBosted()
                YRKESSKADE_V2_VELG_DATO -> it.sorteringYrkesskader()
                MEDLEMSKAP_OPPHOLDSTILLATELSE -> it.sorteringMedlemskapOppholdstillatelse()
                MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING -> it.sorteringMedlemskapArbeidUtenforNorgeGruppering()
                else -> it.tag
            }
        }
    return this.copy(undersporsmal = sorterteUndersporsmal)
}

private fun Sporsmal.sorteringMedlemskapOppholdstillatelse(): String? {
    return when (tag) {
        MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO -> "0"
        MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT -> "1"
        MEDLEMSKAP_OPPHOLDSTILLATELSE_PERIODE -> "2"
        else -> throw RuntimeException("Ukjent underspørsmål for medlemskap oppholdstillatelse: $tag")
    }
}

private fun Sporsmal.sorteringMedlemskapArbeidUtenforNorgeGruppering(): String {
    val tagUtenIndex = fjernIndexFraTag(this.tag)
    return when (tagUtenIndex) {
        MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER -> "0"
        MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR -> "1"
        MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR -> "2"
        else -> throw RuntimeException("Ukjent underspørsmål for medlemskap arbeid utenfor Norge: $tag")
    }
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
        INNTEKTSKILDE_STYREVERV -> "9"
        INNTEKTSKILDE_ANNET -> "9"
        else -> throw RuntimeException("Ukjent underspørsmål for andre inntektskilder: $tag")
    }
}

private fun Sporsmal.sorteringVarigEndring(): String {
    return when (tag) {
        INNTEKTSKILDE_SELVSTENDIG_VARIG_ENDRING_JA -> "0"
        INNTEKTSKILDE_SELVSTENDIG_VARIG_ENDRING_NEI -> "1"
        INNTEKTSKILDE_SELVSTENDIG_VARIG_ENDRING_VET_IKKE -> "2"
        else -> throw RuntimeException("Ukjent underspørsmål for INNTEKTSKILDE_SELVSTENDIG_VARIG_ENDRING_GRUPPE: $tag")
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

private fun Sporsmal.sorteringBosted(): String {
    return when (tag) {
        UTENLANDSK_SYKMELDING_CO -> "0"
        UTENLANDSK_SYKMELDING_VEGNAVN -> "1"
        UTENLANDSK_SYKMELDING_BYGNING -> "2"
        UTENLANDSK_SYKMELDING_BY -> "3"
        UTENLANDSK_SYKMELDING_REGION -> "4"
        UTENLANDSK_SYKMELDING_LAND -> "5"
        UTENLANDSK_SYKMELDING_TELEFONNUMMER -> "6"
        UTENLANDSK_SYKMELDING_GYLDIGHET_ADRESSE -> "7"
        else -> throw RuntimeException("Ukjent underspørsmål for reise med bil: $tag")
    }
}

private fun Sporsmal.sorteringYrkesskader(): String {
    return undertekst ?: "0"
}

/**
 * Fjerner siste del av en tekst(index) fra tag hvis siste del av tag er et tall. Deler er separert med underscore.
 */
fun fjernIndexFraTag(input: String): String {
    val sisteUnderscoreIndex = input.lastIndexOf("_")
    val sisteVerdiEtterUnderscore = input.substring(sisteUnderscoreIndex + 1)

    // Sjekker om siste del av tag er et tall og returnerer da tag uten index hvis det er tilfelle.
    if (sisteVerdiEtterUnderscore.toIntOrNull() != null) {
        return input.substring(0, sisteUnderscoreIndex)
    }
    return input
}
