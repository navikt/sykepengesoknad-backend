package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.NARINGSSPESIFIKASJON_OPTIONAL
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.REGNSKAP_FORELOPIG
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.REGNSKAP_FORRIGE_AAR
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.SKATTEMELDING
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType.SKATTEMELDING_OPTIONAL
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI
import no.nav.helse.flex.soknadsopprettelse.INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT
import java.time.LocalDate

enum class InntektsopplysningerDokumentType(val vedleggsnr: String, val tittel: String) {
    SKATTEMELDING("N6", "Skattemelding"),
    SKATTEMELDING_OPTIONAL("N6", "Skattemelding hvis den er klar"),
    NARINGSSPESIFIKASJON("L1", "Næringsoppgave/Næringsspessifikasjon"),
    NARINGSSPESIFIKASJON_OPTIONAL("N6", "Næringsoppgave/Næringsspessifikasjon hvis den er klar"),
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
                SKATTEMELDING_OPTIONAL,
                NARINGSSPESIFIKASJON_OPTIONAL,
            )

        dagensDato.isAfter(fristSkattemelding) && dagensDato.isBefore(sisteTertialStart) ->
            listOf(
                SKATTEMELDING,
                NARINGSSPESIFIKASJON,
            )

        else -> listOf(SKATTEMELDING, NARINGSSPESIFIKASJON, REGNSKAP_FORELOPIG)
    }
}

fun Sykepengesoknad.inntektsopplysningerMaaDokumenteres(): Boolean {
    getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA)?.let {
        if (it.forsteSvar == "CHECKED") {
            return true
        }
    }

    getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI)?.let { sporsmal1 ->
        if (sporsmal1.forsteSvar == "CHECKED") {
            getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_VARIG_ENDRING_25_PROSENT)?.let {
                if (it.forsteSvar == "JA") {
                    return true
                }
            }
        }
    }
    return false
}
