package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Periode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.TextStyle
import java.util.Locale

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

fun LocalDate.datoMånedÅrFormat(): String = this.format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

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
