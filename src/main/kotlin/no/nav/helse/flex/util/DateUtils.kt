package no.nav.helse.flex.util

import java.time.LocalDate

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
