package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.FTA_INNTEKT_UNDERVEIS

fun Sykepengesoknad.hentInntektUnderveis(): Boolean? {
    getSporsmalMedTagOrNull(FTA_INNTEKT_UNDERVEIS)?.let {
        return "JA" == it.forsteSvar
    }
    return null
}
