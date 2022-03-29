package no.nav.syfo.domain.mapper.sporsmalprossesering

import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.soknadsopprettelse.ARBEID_UTENFOR_NORGE

fun Sykepengesoknad.hentArbeidUtenforNorge(): Boolean? {
    getSporsmalMedTagOrNull(ARBEID_UTENFOR_NORGE)?.let {
        return "JA" == it.forsteSvar
    }
    return null
}
