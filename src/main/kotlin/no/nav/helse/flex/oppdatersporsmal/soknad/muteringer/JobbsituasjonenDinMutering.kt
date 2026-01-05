package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.flatten
import no.nav.helse.flex.oppdatersporsmal.soknad.erIkkeAvType
import no.nav.helse.flex.oppdatersporsmal.soknad.leggTilSporsmaal
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.frisktilarbeid.ftaReiseTilUtlandet
import no.nav.helse.flex.soknadsopprettelse.frisktilarbeid.inntektUnderveis
import java.time.LocalDate

fun Sykepengesoknad.jobbsituasjonenDinMutering(): Sykepengesoknad {
    if (erIkkeAvType(FRISKMELDT_TIL_ARBEIDSFORMIDLING)) {
        return this
    }

    val ftaSpm = sporsmal.find { it.tag == "FTA_JOBBSITUASJONEN_DIN" } ?: return this

    val underspm = listOf(ftaSpm).flatten()

    fun tagHarSvar(
        tag: String,
        svar: String,
    ): Boolean =
        underspm
            .find { it.tag == tag }
            ?.svar
            ?.firstOrNull()
            ?.verdi == svar

    fun finnBesvartDato(): String? =
        when {
            tagHarSvar(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED") -> {
                if (tagHarSvar(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "NEI")) {
                    underspm
                        .find { it.tag == FTA_JOBBSITUASJONEN_DIN_NAR }
                        ?.svar
                        ?.firstOrNull()
                        ?.verdi
                } else {
                    null
                }
            }

            tagHarSvar(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED") -> {
                if (tagHarSvar(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI")) {
                    underspm
                        .find { it.tag == FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR }
                        ?.svar
                        ?.firstOrNull()
                        ?.verdi
                } else {
                    null
                }
            }

            else -> null
        }

    val besvartDato = finnBesvartDato()

    if (besvartDato == this.fom.toString()) {
        // Fjerner spørsmål som forsvinner og returnerer
        return this.copy(
            sporsmal =
                sporsmal
                    .asSequence()
                    .filterNot { (_, tag) -> tag == FTA_INNTEKT_UNDERVEIS }
                    .filterNot { (_, tag) -> tag == FTA_REISE_TIL_UTLANDET }
                    .toMutableList(),
        )
    }

    fun finnDagForBegrensende(): LocalDate {
        if (besvartDato != null) {
            return LocalDate.parse(besvartDato).minusDays(1)
        } else {
            return this.tom!!
        }
    }

    val dagForBegrensende = finnDagForBegrensende()

    return this
        .leggTilSporsmaal(ftaReiseTilUtlandet(this.fom!!, dagForBegrensende))
        .leggTilSporsmaal(inntektUnderveis(this.fom, dagForBegrensende))
}
