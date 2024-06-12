package no.nav.helse.flex.domain

import java.time.DayOfWeek
import java.time.LocalDate

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
) {
    fun sjekkOmDagenErIPeriode(dato: LocalDate): Boolean {
        return dato.isAfter(fom.minusDays(1)) && dato.isBefore(tom.plusDays(1))
    }

    fun hentUkedager(): List<LocalDate> {
        val ukedager = mutableListOf<LocalDate>()
        var current = fom
        while (!current.isAfter(tom)) {
            if (current.dayOfWeek != DayOfWeek.SATURDAY && current.dayOfWeek != DayOfWeek.SUNDAY) {
                ukedager.add(current)
            }
            current = current.plusDays(1)
        }
        return ukedager
    }
}
