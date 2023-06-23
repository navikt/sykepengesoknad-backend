package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.YRKESSKADE
import no.nav.helse.flex.soknadsopprettelse.YRKESSKADE_V2

fun Sykepengesoknad.hentYrkesskade(): Boolean? {
    getSporsmalMedTagOrNull(YRKESSKADE)?.let {
        return "JA" == it.forsteSvar
    }
    getSporsmalMedTagOrNull(YRKESSKADE_V2)?.let {
        return "JA" == it.forsteSvar
    }
    return null
}
