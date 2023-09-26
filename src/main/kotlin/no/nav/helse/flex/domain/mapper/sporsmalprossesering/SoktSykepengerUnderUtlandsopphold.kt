package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSLEDIG_UTLAND
import no.nav.helse.flex.soknadsopprettelse.FERIE_PERMISJON_UTLAND
import no.nav.helse.flex.soknadsopprettelse.UTLAND
import no.nav.helse.flex.soknadsopprettelse.UTLANDSOPPHOLD_SOKT_SYKEPENGER
import no.nav.helse.flex.soknadsopprettelse.UTLAND_V2

fun harSoktSykepengerUnderUtlandsopphold(sykepengesoknad: Sykepengesoknad): Boolean? {
    return if (!harSvartJaPaPermisjonUtland(sykepengesoknad) || !harSvartJaPaUtland(sykepengesoknad)) {
        null
    } else {
        sykepengesoknad.getSporsmalMedTagOrNull(UTLANDSOPPHOLD_SOKT_SYKEPENGER)?.forsteSvar == "JA"
    }
}

private fun harSvartJaPaPermisjonUtland(sykepengesoknad: Sykepengesoknad): Boolean {
    return sykepengesoknad.getSporsmalMedTagOrNull(FERIE_PERMISJON_UTLAND)?.forsteSvar == "JA"
}

private fun harSvartJaPaUtland(sykepengesoknad: Sykepengesoknad): Boolean {
    val utland = sykepengesoknad.getSporsmalMedTagOrNull(UTLAND)?.forsteSvar == "CHECKED"
    val utlandV2 = sykepengesoknad.getSporsmalMedTagOrNull(UTLAND_V2)?.forsteSvar == "CHECKED"
    val utlandArbeidsledig = sykepengesoknad.getSporsmalMedTagOrNull(ARBEIDSLEDIG_UTLAND)?.forsteSvar == "CHECKED"

    return utland || utlandV2 || utlandArbeidsledig
}
