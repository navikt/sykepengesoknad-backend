package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.getJsonPeriode
import no.nav.helse.flex.soknadsopprettelse.PAPIRSYKMELDING_NAR
import no.nav.helse.flex.soknadsopprettelse.TIDLIGERE_PAPIRSYKMELDING
import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
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
