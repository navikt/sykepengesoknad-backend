package no.nav.helse.flex.domain.mapper.sporsmalprossesering

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.ENKELTSTAENDE_BEHANDLINGSDAGER_UKE
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

fun Sykepengesoknad.hentBehandlingsdager(): List<LocalDate> {
    return this.alleSporsmalOgUndersporsmal()
        .filter { it.tag.startsWith(ENKELTSTAENDE_BEHANDLINGSDAGER_UKE) }
        .filter { it.svar.size == 1 }
        .mapNotNull {
            try {
                LocalDate.parse(it.svar.first().verdi, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (e: DateTimeParseException) {
                null
            }
        }
}
