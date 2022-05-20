package no.nav.helse.flex.util

import java.time.DayOfWeek.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun LocalDate.isAfterOrEqual(other: LocalDate): Boolean {
    return this == other || this.isAfter(other)
}

fun LocalDate.isBeforeOrEqual(other: LocalDate): Boolean {
    return this == other || this.isBefore(other)
}

fun LocalDate.erHelg(): Boolean {
    return this.dayOfWeek == SUNDAY || this.dayOfWeek == SATURDAY
}

fun LocalDate.datoMånedÅrFormat(): String? {
    return this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))
}

fun LocalDate.forsteHverdag(): LocalDate {
    var day = this
    while (day.erHelg()) {
        day = day.plusDays(1)
    }
    return day
}

fun LocalDate.fredagISammeUke(): LocalDate {
    if (dayOfWeek == SUNDAY) {
        return minusDays(2)
    }
    if (dayOfWeek == SATURDAY) {
        return minusDays(1)
    }
    var day = this
    while (day.dayOfWeek != FRIDAY) {
        day = day.plusDays(1)
    }
    return day
}

/**
 * Så lenge de har minst en dato til felles
 */
fun ClosedRange<LocalDate>.overlap(other: ClosedRange<LocalDate>): Boolean {
    return this.start in other || this.endInclusive in other || other.start in this || other.endInclusive in this
}
