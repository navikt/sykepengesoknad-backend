package no.nav.helse.flex.domain

import java.time.LocalDate

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate,
) {
    fun erIPeriode(dato: LocalDate): Boolean {
        return dato.isAfter(fom.minusDays(1)) && dato.isBefore(tom.plusDays(1))
    }
}
