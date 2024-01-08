package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad

fun Sykepengesoknad.sorterUndersporsmal(): Sykepengesoknad {
    return this
        .copy(
            sporsmal =
                this.sporsmal.map {
                    it.sorterUndersporsmal()
                },
        )
}

private fun Sporsmal.sorterUndersporsmal(): Sporsmal {
    val sorterteUndersporsmal =
        this.undersporsmal
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
                    MEDLEMSKAP_OPPHOLDSTILLATELSE_GRUPPE -> it.sorteringMedlemskapOppholdstillatelseGruppering()
                    MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_GRUPPERING -> it.sorteringMedlemskapArbeidUtenforNorgeGruppering()
                    MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_GRUPPERING -> it.sorteringMedlemskapOppholdUtenforNorgeGruppering()
                    MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE -> it.sorteringMedlemskapOppholdUtenforNorgeBegrunnelse()
                    MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_GRUPPERING -> it.sorteringMedlemskapOppholdUtenforEosGruppering()
                    MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE -> it.sorteringMedlemskapOppholdUtenforEosBegrunnelse()
                    KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET -> it.sorteringKjenteInntektskilderArsak()
                    INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE -> it.sorteringVarigEndringBegrunnelse()
                    else -> it.tag
                }
            }
    return this.copy(undersporsmal = sorterteUndersporsmal)
}

private fun Sporsmal.sorteringVarigEndringBegrunnelse(): String {
    return when (tag) {
        INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_OPPRETTELSE_NEDLEGGELSE -> "0"
        INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ENDRET_INNSATS -> "1"
        INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_OMLEGGING_AV_VIRKSOMHETEN -> "2"
        INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ENDRET_MARKEDSSITUASJON -> "3"
        INNTEKTSOPPLYSNINGER_VARIG_ENDRING_BEGRUNNELSE_ANNET -> "4"
        else -> throw RuntimeException("Ukjent underspørsmål for varig endring begrunnelse: $tag")
    }
}

private fun Sporsmal.sorteringMedlemskapOppholdstillatelse(): String {
    return when (tag) {
        MEDLEMSKAP_OPPHOLDSTILLATELSE_VEDTAKSDATO -> "0"
        MEDLEMSKAP_OPPHOLDSTILLATELSE_GRUPPE -> "1"
        else -> throw RuntimeException("Ukjent underspørsmål for medlemskap oppholdstillatelse: $tag")
    }
}

private fun Sporsmal.sorteringMedlemskapOppholdstillatelseGruppering(): String {
    return when (tag) {
        MEDLEMSKAP_OPPHOLDSTILLATELSE_MIDLERTIDIG -> "0"
        MEDLEMSKAP_OPPHOLDSTILLATELSE_PERMANENT -> "1"
        else -> throw RuntimeException("Ukjent underspørsmål for medlemskap oppholdstillatelse: $tag")
    }
}

private fun Sporsmal.sorteringMedlemskapArbeidUtenforNorgeGruppering(): String {
    val tagUtenIndex = fjernIndexFraTag(this.tag)
    return when (tagUtenIndex) {
        MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_HVOR -> "0"
        MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_ARBEIDSGIVER -> "1"
        MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE_NAAR -> "2"
        else -> throw RuntimeException("Ukjent underspørsmål for medlemskap arbeid utenfor Norge: $tag")
    }
}

private fun Sporsmal.sorteringMedlemskapOppholdUtenforNorgeGruppering(): String {
    val tagUtenIndex = fjernIndexFraTag(this.tag)
    return when (tagUtenIndex) {
        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_HVOR -> "0"
        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE -> "1"
        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_NAAR -> "2"
        else -> throw RuntimeException("Ukjent underspørsmål for medlemskap opphold utenfor Norge: $tag")
    }
}

private fun Sporsmal.sorteringMedlemskapOppholdUtenforNorgeBegrunnelse(): String {
    val tagUtenIndex = fjernIndexFraTag(this.tag)
    return when (tagUtenIndex) {
        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_STUDIE -> "0"
        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_FERIE -> "1"
        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_BO -> "2"
        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_EKTEFELLE -> "3"
        MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE_BEGRUNNELSE_ANNET -> "4"
        else -> throw RuntimeException("Ukjent underspørsmål for begrunnelse for opphold utenfor Norge: $tag")
    }
}

private fun Sporsmal.sorteringMedlemskapOppholdUtenforEosGruppering(): String {
    val tagUtenIndex = fjernIndexFraTag(this.tag)
    return when (tagUtenIndex) {
        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_HVOR -> "0"
        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE -> "1"
        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_NAAR -> "2"
        else -> throw RuntimeException("Ukjent underspørsmål for medlemskap opphold utenfor EØS: $tag")
    }
}

private fun Sporsmal.sorteringMedlemskapOppholdUtenforEosBegrunnelse(): String {
    val tagUtenIndex = fjernIndexFraTag(this.tag)
    return when (tagUtenIndex) {
        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_STUDIE -> "0"
        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_FERIE -> "1"
        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_BO -> "2"
        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_EKTEFELLE -> "3"
        MEDLEMSKAP_OPPHOLD_UTENFOR_EOS_BEGRUNNELSE_ANNET -> "4"
        else -> throw RuntimeException("Ukjent underspørsmål for begrunnelse for opphold utenfor EØS: $tag")
    }
}

private fun Sporsmal.sorteringKjenteInntektskilderArsak(): String {
    val tagUtenIndex = fjernIndexFraTag(this.tag)
    return when (tagUtenIndex) {
        KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_SYKMELDT -> "0"
        KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_TURNUS -> "1"
        KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_FERIE -> "2"
        KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_AVSPASERING -> "3"
        KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_PERMITTERT -> "4"
        KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_PERMISJON -> "5"
        KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_TILKALLINGSVIKAR -> "6"
        KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_ANNEN -> "7"
        else -> throw RuntimeException("Ukjent underspørsmål for kjente inntektskilder årsak: $tag")
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
 * Fjerner siste del av en tekst(index) fra tag hvis siste del av tag er et tall. Antar at underscore er brukt som
 * separator.
 */
fun fjernIndexFraTag(input: String): String {
    val sisteUnderscoreIndex = input.lastIndexOf("_")
    val sisteVerdiEtterUnderscore = input.substring(sisteUnderscoreIndex + 1)

    // Sjekker om siste del av tag er et tall og returnerer da tag uten index, men med underscore hvis det er tilfelle.
    if (sisteVerdiEtterUnderscore.toIntOrNull() != null) {
        return input.substring(0, sisteUnderscoreIndex + 1)
    }
    return input
}

/**
 * Returnerer høyeste index til et spørsmål. Antar at underscore er brukt som separator.
 */
fun finnHoyesteIndex(sporsmal: List<Sporsmal>): Int {
    return sporsmal.map { it.tag }
        .map { it.substring(it.lastIndexOf("_") + 1) }
        .maxOfOrNull { it.toIntOrNull() ?: 0 } ?: 0
}
