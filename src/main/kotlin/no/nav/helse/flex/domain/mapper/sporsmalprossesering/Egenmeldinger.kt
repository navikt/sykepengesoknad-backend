package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.getJsonPeriode
import no.nav.helse.flex.soknadsopprettelse.EGENMELDINGER_NAR
import no.nav.helse.flex.soknadsopprettelse.TIDLIGERE_EGENMELDING
import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
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
