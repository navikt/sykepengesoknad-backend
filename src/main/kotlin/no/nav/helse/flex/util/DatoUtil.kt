package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Periode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.stream.LongStream

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

fun parseGyldigDato(dato: String?): LocalDate? {
    if (dato == null) {
        return null
    }
    return try {
        LocalDate.parse(dato, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (_: DateTimeParseException) {
        try {
            LocalDate.parse(dato, PeriodeMapper.sporsmalstekstFormat)
        } catch (_: DateTimeParseException) {
            null
        }
    }
}

fun LocalDate.datoMånedÅrFormat(): String = this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

object DatoUtil {
    private fun ukedag(dato: LocalDate): String = dato.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("nb-NO"))

    private fun mnd(dato: LocalDate): String = dato.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("nb-NO"))

    fun formatterPeriode(
        fom: LocalDate,
        tom: LocalDate,
    ): String =
        fom.dayOfMonth.toString() + "." +
            (if (fom.month == tom.month) "" else " " + mnd(fom)) +
            (if (fom.year == tom.year) "" else " " + fom.year) +
            " - " + formatterDato(tom)

    fun formatterDato(dato: LocalDate): String =
        dato.dayOfMonth.toString() + ". " +
            mnd(dato) + " " +
            dato.year

    fun formatterDatoUtenÅr(dato: LocalDate): String =
        dato.dayOfMonth.toString() + ". " +
            mnd(dato)

    fun formatterDatoMedUkedag(dato: LocalDate): String = ukedag(dato) + " " + formatterDato(dato)

    fun periodeErUtenforHelg(periode: Periode): Boolean =
        ChronoUnit.DAYS.between(periode.fom, periode.tom) > 1 ||
            !periode.fom.erHelg() ||
            !periode.tom.erHelg()

    fun periodeHarDagerUtenforAndrePerioder(
        periode: Periode,
        andrePerioder: List<Periode>,
    ): Boolean =
        LongStream
            .rangeClosed(0, ChronoUnit.DAYS.between(periode.fom, periode.tom))
            .mapToObj { i: Long -> periode.fom.plusDays(i) }
            .anyMatch { dagIPeriode: LocalDate? ->
                andrePerioder
                    .stream()
                    .noneMatch { annenPeriode: Periode ->
                        annenPeriode.erIPeriode(dagIPeriode!!)
                    }
            }

    fun periodeTilJson(
        fom: LocalDate,
        tom: LocalDate,
    ): String =
        "{\"fom\":\"" + fom.format(DateTimeFormatter.ISO_LOCAL_DATE) +
            "\",\"tom\":\"" + tom.format(DateTimeFormatter.ISO_LOCAL_DATE) + "\"}"

    fun datoErInnenforMinMax(
        dato: LocalDate,
        min: String?,
        max: String?,
    ): Boolean = !dato.isBefore(mapNullTilLocalDateMIN(min)) && !dato.isAfter(mapNullTilLocalDateMAX(max))

    fun periodeErInnenforMinMax(
        periode: Periode,
        min: String?,
        max: String?,
    ): Boolean = datoErInnenforMinMax(periode.fom, min, max) && datoErInnenforMinMax(periode.tom, min, max)

    private fun mapNullTilLocalDateMIN(min: String?): LocalDate =
        if (min == null) LocalDate.MIN else LocalDate.parse(min, DateTimeFormatter.ISO_LOCAL_DATE)

    private fun mapNullTilLocalDateMAX(max: String?): LocalDate =
        if (max == null) LocalDate.MAX else LocalDate.parse(max, DateTimeFormatter.ISO_LOCAL_DATE)
}
