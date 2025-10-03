package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.*

fun Sykepengesoknad.hentHovedSporsmalSvarForSelvstendigNaringsdrivende(): Map<String, Boolean> {
    val hovedSporsmalSvar = finnHovedJaNeiSvar()

    finnCheckedSvar(
        sporsmal = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
        jaSvar = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_JA,
        neiSvar = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_NEI,
    )?.also { hovedSporsmalSvar[it.first] = it.second }

    finnCheckedSvar(
        sporsmal = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET,
        jaSvar = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_JA,
        neiSvar = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI,
    )?.also { hovedSporsmalSvar[it.first] = it.second }

    getSporsmalMedTagOrNull(INNTEKTSOPPLYSNINGER_VARIG_ENDRING)?.forsteSvar?.let {
        hovedSporsmalSvar[INNTEKTSOPPLYSNINGER_VARIG_ENDRING] = it == "JA"
    }

    return hovedSporsmalSvar
}

private fun Sykepengesoknad.finnHovedJaNeiSvar(): MutableMap<String, Boolean> =
    sporsmal
        .filter { it.svartype == Svartype.JA_NEI && it.forsteSvar != null }
        .associate { it.tag to (it.forsteSvar == "JA") }
        .toMutableMap()

private fun Sykepengesoknad.finnCheckedSvar(
    sporsmal: String,
    jaSvar: String,
    neiSvar: String,
): Pair<String, Boolean>? =
    when {
        getSporsmalMedTagOrNull(jaSvar)?.forsteSvar == "CHECKED" -> sporsmal to true
        getSporsmalMedTagOrNull(neiSvar)?.forsteSvar == "CHECKED" -> sporsmal to false
        else -> null
    }
