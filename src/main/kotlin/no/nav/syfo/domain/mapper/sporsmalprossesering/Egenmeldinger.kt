package no.nav.syfo.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.getJsonPeriode
import no.nav.syfo.soknadsopprettelse.EGENMELDINGER_NAR
import no.nav.syfo.soknadsopprettelse.TIDLIGERE_EGENMELDING
import java.util.*

fun hentEgenmeldinger(sykepengesoknad: Sykepengesoknad): List<PeriodeDTO> {
    val sporsmal = sykepengesoknad.getSporsmalMedTagOrNull(TIDLIGERE_EGENMELDING)
    if (null != sporsmal) {
        if ("CHECKED" == sporsmal.forsteSvar) {
            val sporsmalMedTag = sykepengesoknad.getSporsmalMedTag(EGENMELDINGER_NAR)
            val map = sporsmalMedTag.svar
                .map { svar -> svar.verdi.getJsonPeriode() }
            return map
        }
    }
    return Collections.emptyList()
}
