package no.nav.syfo.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.domain.mapper.getJsonPeriode
import no.nav.syfo.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.syfo.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN_NAR
import java.util.*

fun hentFravarForSykmeldingen(sykepengesoknad: Sykepengesoknad): List<PeriodeDTO> {
    val sporsmal = sykepengesoknad.getSporsmalMedTagOrNull(FRAVAR_FOR_SYKMELDINGEN)
    if (null != sporsmal) {
        if ("JA" == sporsmal.forsteSvar) {
            val sporsmalMedTag = sykepengesoknad.getSporsmalMedTag(FRAVAR_FOR_SYKMELDINGEN_NAR)
            val map = sporsmalMedTag.svar
                .map { svar -> svar.verdi.getJsonPeriode() }
            return map
        }
    }
    return Collections.emptyList()
}
