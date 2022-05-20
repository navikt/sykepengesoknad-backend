package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE

fun Sykepengesoknad.hentArbeidUtenforNorge(): Boolean? {
    getSporsmalMedTagOrNull(ARBEID_UTENFOR_NORGE)?.let {
        return "JA" == it.forsteSvar
    }
    return null
}
