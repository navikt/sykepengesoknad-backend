package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON_OPTIONAL
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.REGNSKAP_FORELOPIG
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.REGNSKAP_FORRIGE_AAR
import no.nav.helse.flex.soknadsopprettelse.*
import java.time.LocalDate

enum class InntektsopplysningerDokumentType(
    val vedleggsnr: String,
    val tittel: String,
) {
    NARINGSSPESIFIKASJON("L1", "Skattemelding/Næringsspesifikasjon"),
    NARINGSSPESIFIKASJON_OPTIONAL("N6", "Skattemelding/Næringsspesifikasjon hvis den er klar"),
    REGNSKAP_FORRIGE_AAR("L3", "Regnskap for siste år"),
    REGNSKAP_FORELOPIG("L3", "Foreløpig regnskap i år"),
}

fun dokumenterSomSkalSendesInn(dagensDato: LocalDate): List<InntektsopplysningerDokumentType> {
    val sisteTertialStart = LocalDate.of(dagensDato.year, 9, 1)
    val fristSkattemelding = LocalDate.of(dagensDato.year, 5, 31)

    return when {
        dagensDato.isBefore(fristSkattemelding) ->
            listOf(
                REGNSKAP_FORRIGE_AAR,
                NARINGSSPESIFIKASJON_OPTIONAL,
            )

        dagensDato.isAfter(fristSkattemelding) && dagensDato.isBefore(sisteTertialStart) ->
            listOf(
                NARINGSSPESIFIKASJON,
            )

        else -> listOf(NARINGSSPESIFIKASJON, REGNSKAP_FORELOPIG)
    }
}

fun Sykepengesoknad.inntektsopplysningerMaaDokumenteres(): Boolean =
    getSporsmalMedTagOrNull(NARINGSDRIVENDE_NY_I_ARBEIDSLIVET)?.forsteSvar == "JA"
