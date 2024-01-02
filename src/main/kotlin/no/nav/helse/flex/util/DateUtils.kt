package no.nav.helse.flex.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

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

fun parseGyldigDato(dato: String?): LocalDate? {
    if (dato == null) {
        return null
    }
    return try {
        LocalDate.parse(dato, DateTimeFormatter.ISO_LOCAL_DATE)
    } catch (dateTimeParseException: DateTimeParseException) {
        try {
            LocalDate.parse(dato, PeriodeMapper.sporsmalstekstFormat)
        } catch (dateTimeParseException2: DateTimeParseException) {
            null
        }
    }
}
