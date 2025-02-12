package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.flatten
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_FORTSATT_ARBEIDSSOKER
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_FORTSATT_ARBEIDSSOKER_NY_JOBB
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_JA
import no.nav.helse.flex.soknadsopprettelse.FTA_JOBBSITUASJONEN_DIN_NEI

fun Sykepengesoknad.hentFortsattArbeidssoker(): Boolean? {
    val alleSpm = this.sporsmal.flatten()
    val begyntINyJobb = alleSpm.firstOrNull { it.tag == FTA_JOBBSITUASJONEN_DIN_JA }?.forsteSvar == "CHECKED"
    val fortsattArbeidssokerSporsmal =
        alleSpm.firstOrNull { it.tag == FTA_JOBBSITUASJONEN_DIN_FORTSATT_ARBEIDSSOKER_NY_JOBB }
    val begyntINyJobbFortsattArbeidssoker = fortsattArbeidssokerSporsmal?.forsteSvar == "JA"
    val ingenNyJobb = alleSpm.firstOrNull { it.tag == FTA_JOBBSITUASJONEN_DIN_NEI }?.forsteSvar == "CHECKED"
    val fortsattArbeidssokerSporsmalIngenNyJobb =
        alleSpm.firstOrNull { it.tag == FTA_JOBBSITUASJONEN_DIN_FORTSATT_ARBEIDSSOKER }
    val ingenNyJobbFortsattArbeidssoker = fortsattArbeidssokerSporsmalIngenNyJobb?.forsteSvar == "JA"

    if (fortsattArbeidssokerSporsmal != null && fortsattArbeidssokerSporsmalIngenNyJobb != null) {
        if (begyntINyJobb && begyntINyJobbFortsattArbeidssoker) {
            return true
        }
        if (ingenNyJobb && ingenNyJobbFortsattArbeidssoker) {
            return true
        }

        return false
    }

    return null
}
