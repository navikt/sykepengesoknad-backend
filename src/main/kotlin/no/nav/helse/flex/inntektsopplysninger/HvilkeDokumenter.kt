package no.nav.helse.flex.inntektsopplysninger

import no.nav.helse.flex.inntektsopplysninger.DokumentTyper.NARINGSSPESIFIKASJON
import no.nav.helse.flex.inntektsopplysninger.DokumentTyper.NARINGSSPESIFIKASJON_OPTIONAL
import no.nav.helse.flex.inntektsopplysninger.DokumentTyper.REGNSKAP_FORELOPIG
import no.nav.helse.flex.inntektsopplysninger.DokumentTyper.REGNSKAP_FORRIGE_AAR
import no.nav.helse.flex.inntektsopplysninger.DokumentTyper.SKATTEMELDING
import no.nav.helse.flex.inntektsopplysninger.DokumentTyper.SKATTEMELDING_OPTIONAL
import java.time.LocalDate


enum class DokumentTyper {
    SKATTEMELDING,
    SKATTEMELDING_OPTIONAL,
    NARINGSSPESIFIKASJON,
    NARINGSSPESIFIKASJON_OPTIONAL,
    REGNSKAP_FORRIGE_AAR,
    REGNSKAP_FORELOPIG
}

internal fun dokumenterSomkalSendes(dagensDato: LocalDate): List<DokumentTyper> {
    val sisteTertialStart = LocalDate.of(dagensDato.year, 9, 1)
    val fristSkattemelding = LocalDate.of(dagensDato.year, 5, 31)

    return when {
        dagensDato.isBefore(fristSkattemelding) -> listOf(
            REGNSKAP_FORRIGE_AAR,
            SKATTEMELDING_OPTIONAL,
            NARINGSSPESIFIKASJON_OPTIONAL
        )

        dagensDato.isAfter(fristSkattemelding) && dagensDato.isBefore(sisteTertialStart) -> listOf(
            SKATTEMELDING,
            NARINGSSPESIFIKASJON
        )

        else -> listOf(SKATTEMELDING, NARINGSSPESIFIKASJON, REGNSKAP_FORELOPIG)
    }
}
