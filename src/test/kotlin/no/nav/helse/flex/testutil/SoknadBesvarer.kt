package no.nav.helse.flex.testutil

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.oppdaterSporsmal
import no.nav.helse.flex.sendSoknadMedResult
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

fun RSSporsmal.byttSvar(tag: String? = null, svar: String): RSSporsmal =
    this.byttSvar(tag, listOf(svar))

fun RSSporsmal.byttSvar(tag: String? = null, svar: List<String>): RSSporsmal =
    listOf(this).byttSvar(tag ?: this.tag, svar.toList().map { RSSvar(verdi = it) }).first()

private fun List<RSSporsmal>.byttSvar(tag: String, svar: List<RSSvar>): List<RSSporsmal> {
    return map { spm ->
        when {
            spm.tag == tag -> spm.copy(svar = svar)
            spm.undersporsmal.isNotEmpty() -> spm.copy(
                undersporsmal = spm.undersporsmal.byttSvar(
                    tag,
                    svar
                )
            )
            else -> spm
        }
    }
}

private fun RSSykepengesoknad.byttSvar(tag: String, svar: List<RSSvar>): RSSykepengesoknad =
    copy(sporsmal = sporsmal?.byttSvar(tag, svar))

private fun RSSporsmal.erSporsmalMedIdEllerHarUndersporsmalMedId(id: String): Boolean =
    this.id == id || this.undersporsmal.any { it.erSporsmalMedIdEllerHarUndersporsmalMedId(id) } || this.undersporsmal.any { it.id == id }

class SoknadBesvarer(
    var rSSykepengesoknad: RSSykepengesoknad,
    val mockMvc: BaseTestClass, // TODO RENAME
    val fnr: String,
    val muterteSoknaden: Boolean = false
) {
    fun besvarSporsmal(tag: String, svar: String, ferdigBesvart: Boolean = true): SoknadBesvarer {
        return besvarSporsmal(tag, listOf(svar), ferdigBesvart)
    }

    fun besvarSporsmal(tag: String, svarListe: List<String>, ferdigBesvart: Boolean = true): SoknadBesvarer {
        val sporsmal = rSSykepengesoknad.alleSporsmalOgUndersporsmal().find { it.tag == tag }
            ?: throw RuntimeException("Spørsmål ikke funnet $tag")
        val rsSvar = svarListe.map { RSSvar(verdi = it) }
        val oppdatertSoknad = rSSykepengesoknad.byttSvar(sporsmal.tag, rsSvar)
        rSSykepengesoknad = oppdatertSoknad
        return if (ferdigBesvart) {
            gaVidere(tag)
        } else {
            this
        }
    }

    fun gaVidere(tag: String): SoknadBesvarer {
        val hovedsporsmal = finnHovedsporsmal(tag)
        val (mutertSoknad, _) = mockMvc.oppdaterSporsmal(fnr, hovedsporsmal, rSSykepengesoknad.id)

        return SoknadBesvarer(
            rSSykepengesoknad = mutertSoknad ?: this.rSSykepengesoknad,
            mockMvc = mockMvc,
            fnr = fnr,
            muterteSoknaden = mutertSoknad != null
        )
    }

    fun finnHovedsporsmal(tag: String): RSSporsmal {
        val sporsmal = rSSykepengesoknad.alleSporsmalOgUndersporsmal().find { it.tag == tag }
            ?: throw RuntimeException("Spørsmål ikke funnet $tag")
        return rSSykepengesoknad.sporsmal!!.first { it.erSporsmalMedIdEllerHarUndersporsmalMedId(sporsmal.id!!) }
    }

    fun sendSoknad(): RSSykepengesoknad {
        mockMvc.sendSoknadMedResult(fnr, rSSykepengesoknad.id).andExpect(((MockMvcResultMatchers.status().isOk)))
        return mockMvc.hentSoknader(fnr).first { it.id == rSSykepengesoknad.id }
    }
}
