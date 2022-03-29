package no.nav.syfo.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.getJsonPeriode
import no.nav.syfo.soknadsopprettelse.PAPIRSYKMELDING_NAR
import no.nav.syfo.soknadsopprettelse.TIDLIGERE_PAPIRSYKMELDING
import java.util.*

fun hentPapirsykmeldinger(sykepengesoknad: Sykepengesoknad): List<PeriodeDTO> {
    val sporsmal = sykepengesoknad.getSporsmalMedTagOrNull(TIDLIGERE_PAPIRSYKMELDING)
    if (null != sporsmal) {
        if ("CHECKED" == sporsmal.forsteSvar) {
            return sykepengesoknad.getSporsmalMedTag(PAPIRSYKMELDING_NAR).svar
                .map { svar -> svar.verdi.getJsonPeriode() }
        }
    }
    return Collections.emptyList()
}
