package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.getJsonPeriode
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN
import no.nav.helse.flex.soknadsopprettelse.FRAVAR_FOR_SYKMELDINGEN_NAR
import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
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
