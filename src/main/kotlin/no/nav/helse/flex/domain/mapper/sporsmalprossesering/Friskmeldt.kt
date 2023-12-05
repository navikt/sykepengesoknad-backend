package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT_START
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Sykepengesoknad.friskmeldtDato(): LocalDate? {
    getSporsmalMedTagOrNull(FRISKMELDT_START)?.forsteSvar?.let {
        if ("NEI" == getSporsmalMedTag(FRISKMELDT).forsteSvar) {
            return LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }
    return null
}
