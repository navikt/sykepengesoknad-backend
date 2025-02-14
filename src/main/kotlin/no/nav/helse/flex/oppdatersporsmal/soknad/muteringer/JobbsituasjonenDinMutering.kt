package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.flatten
import no.nav.helse.flex.oppdatersporsmal.soknad.erIkkeAvType
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
    ): Boolean {
        return underspm.find { it.tag == tag }?.svar?.firstOrNull()?.verdi == svar
    }

    fun finnBegrensendeDato(): String? {
        return when {
            tagHarSvar(FTA_JOBBSITUASJONEN_DIN_JA, "CHECKED") -> {
                if (tagHarSvar(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_NY_JOBB, "NEI")) {
                    underspm.find { it.tag == FTA_JOBBSITUASJONEN_DIN_NAR }?.svar?.firstOrNull()?.verdi
                } else {
                    null
                }
            }

            tagHarSvar(FTA_JOBBSITUASJONEN_DIN_NEI, "CHECKED") -> {
                if (tagHarSvar(FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT, "NEI")) {
                    underspm.find { it.tag == FTA_JOBBSITUASJONEN_DIN_FORTSATT_FRISKMELDT_AVREGISTRERT_NAR }?.svar?.firstOrNull()?.verdi
                } else {
                    null
                }
            }

            else -> null
        }
    }

    val begrensendeDato = finnBegrensendeDato()

    if (begrensendeDato != null) {
        val dagForBegrensende = LocalDate.parse(begrensendeDato).minusDays(1).toString()

        var oppdaterteSporsmal =
            sporsmal.filter { spm ->
                listOf(FTA_INNTEKT_UNDERVEIS, FTA_REISE_TIL_UTLANDET)
                    .none { tag -> spm.tag.contains(tag) }
            }

        // Dersom den begrensende datoen ikke er lik søknadens fom, legg til nye spørsmål
        if (begrensendeDato != this.fom.toString()) {
            val inntekt = inntektUnderveis(this.fom!!, LocalDate.parse(dagForBegrensende))
            val reiste = ftaReiseTilUtlandet(this.fom, LocalDate.parse(dagForBegrensende))
            val mutableSporsmal = oppdaterteSporsmal.toMutableList()

            mutableSporsmal.add(inntekt)
            mutableSporsmal.add(reiste)
            oppdaterteSporsmal = mutableSporsmal
        }

        return this.copy(sporsmal = oppdaterteSporsmal)
    }
    return this
}
