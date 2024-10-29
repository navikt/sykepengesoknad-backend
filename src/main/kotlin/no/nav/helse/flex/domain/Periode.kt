package no.nav.helse.flex.domain

import no.nav.helse.flex.util.isBeforeOrEqual
import java.time.DayOfWeek
import java.time.LocalDate

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
) {
    fun erIPeriode(dato: LocalDate): Boolean {
        return dato.isAfter(fom.minusDays(1)) && dato.isBefore(tom.plusDays(1))
    }

    fun hentUkedager(): List<LocalDate> {
        return generateSequence(fom) { it.plusDays(1) }
            .takeWhile { it.isBeforeOrEqual(tom) }
            .filter { it.dayOfWeek != DayOfWeek.SATURDAY && it.dayOfWeek != DayOfWeek.SUNDAY }
            .toList()
    }

    fun overlapper(andre: Periode) =
        (this.fom >= andre.fom && this.fom <= andre.tom) ||
            (this.tom <= andre.tom && this.tom >= andre.fom)
}
