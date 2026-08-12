package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Periode
import java.time.DayOfWeek
import java.time.LocalDate
import no.nav.helse.flex.util.datoErInnenforMinMax as datoErInnenforMinMaxTop
import no.nav.helse.flex.util.formatterDato as formatterDatoTop
import no.nav.helse.flex.util.formatterDatoMedUkedag as formatterDatoMedUkedagTop
import no.nav.helse.flex.util.formatterDatoUtenÅr as formatterDatoUtenÅrTop
import no.nav.helse.flex.util.formatterPeriode as formatterPeriodeTop
import no.nav.helse.flex.util.periodeErInnenforMinMax as periodeErInnenforMinMaxTop
import no.nav.helse.flex.util.periodeErUtenforHelg as periodeErUtenforHelgTop
import no.nav.helse.flex.util.periodeHarDagerUtenforAndrePerioder as periodeHarDagerUtenforAndrePerioderTop
import no.nav.helse.flex.util.periodeTilJson as periodeTilJsonTop

fun max(
    a: LocalDate,
    b: LocalDate,
): LocalDate {
    if (a.isAfter(b)) {
        return a
    }
    return b
}

fun min(
    a: LocalDate,
    b: LocalDate,
): LocalDate {
    if (a.isBefore(b)) {
        return a
    }
    return b
}

fun LocalDate.isAfterOrEqual(other: LocalDate): Boolean = this == other || this.isAfter(other)

fun LocalDate.isBeforeOrEqual(other: LocalDate): Boolean = this == other || this.isBefore(other)

fun LocalDate.erHelg(): Boolean = this.dayOfWeek == DayOfWeek.SATURDAY || this.dayOfWeek == DayOfWeek.SUNDAY

fun LocalDate.erUkedag(): Boolean = !erHelg()

fun LocalDate.erFredag(): Boolean = this.dayOfWeek == DayOfWeek.FRIDAY

fun LocalDate.forsteHverdag(): LocalDate {
    var day = this
    while (day.erHelg()) {
        day = day.plusDays(1)
    }
    return day
}

fun LocalDate.fredagISammeUke(): LocalDate {
    if (dayOfWeek == DayOfWeek.SUNDAY) {
        return minusDays(2)
    }
    if (dayOfWeek == DayOfWeek.SATURDAY) {
        return minusDays(1)
    }
    var day = this
    while (day.dayOfWeek != DayOfWeek.FRIDAY) {
        day = day.plusDays(1)
    }
    return day
}

object DatoUtil {
    fun formatterPeriode(
        fom: LocalDate,
        tom: LocalDate,
    ): String = formatterPeriodeTop(fom, tom)

    fun formatterDato(dato: LocalDate): String = formatterDatoTop(dato)

    fun formatterDatoUtenÅr(dato: LocalDate): String = formatterDatoUtenÅrTop(dato)

    fun formatterDatoMedUkedag(dato: LocalDate): String = formatterDatoMedUkedagTop(dato)

    fun periodeErUtenforHelg(periode: Periode): Boolean = periodeErUtenforHelgTop(periode)

    fun periodeHarDagerUtenforAndrePerioder(
        periode: Periode,
        andrePerioder: List<Periode>,
    ): Boolean = periodeHarDagerUtenforAndrePerioderTop(periode, andrePerioder)

    fun periodeTilJson(
        fom: LocalDate,
        tom: LocalDate,
    ): String = periodeTilJsonTop(fom, tom)

    fun datoErInnenforMinMax(
        dato: LocalDate,
        min: String?,
        max: String?,
    ): Boolean = datoErInnenforMinMaxTop(dato, min, max)

    fun periodeErInnenforMinMax(
        periode: Periode,
        min: String?,
        max: String?,
    ): Boolean = periodeErInnenforMinMaxTop(periode, min, max)
}
