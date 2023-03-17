package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.YRKESSKADE

fun Sykepengesoknad.hentYrkesskade(): Boolean? {
    getSporsmalMedTagOrNull(YRKESSKADE)?.let {
        return "JA" == it.forsteSvar
    }
    return null
}
