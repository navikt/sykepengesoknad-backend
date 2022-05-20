package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Periode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.stream.LongStream

object DatoUtil {

    private fun ukedag(dato: LocalDate): String {
        return dato.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("nb-NO"))
    }

    private fun mnd(dato: LocalDate): String {
        return dato.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("nb-NO"))
    }

    fun formatterPeriode(fom: LocalDate, tom: LocalDate): String {
        return fom.dayOfMonth.toString() + "." +
            (if (fom.month == tom.month) "" else " " + mnd(fom)) +
            (if (fom.year == tom.year) "" else " " + fom.year) +
            " - " + formatterDato(tom)
    }

    fun formatterDato(dato: LocalDate): String {
        return dato.dayOfMonth.toString() + ". " +
            mnd(dato) + " " +
            dato.year
    }

    fun formatterDatoUtenÅr(dato: LocalDate): String {
        return dato.dayOfMonth.toString() + ". " +
            mnd(dato)
    }

    fun formatterDatoMedUkedag(dato: LocalDate): String {
        return ukedag(dato) + " " + formatterDato(dato)
    }

    fun periodeErUtenforHelg(periode: Periode): Boolean {
        return (
            ChronoUnit.DAYS.between(periode.fom, periode.tom) > 1 || !datoErIHelg(periode.fom) ||
                !datoErIHelg(periode.tom)
            )
    }

    private fun datoErIHelg(dato: LocalDate): Boolean {
        return DayOfWeek.SATURDAY == dato.dayOfWeek || DayOfWeek.SUNDAY == dato.dayOfWeek
    }

    fun periodeHarDagerUtenforAndrePerioder(periode: Periode, andrePerioder: List<Periode>): Boolean {
        return LongStream.rangeClosed(0, ChronoUnit.DAYS.between(periode.fom, periode.tom))
            .mapToObj { i: Long -> periode.fom.plusDays(i) }
            .anyMatch { dagIPeriode: LocalDate? ->
                andrePerioder.stream()
                    .noneMatch { annenPeriode: Periode ->
                        annenPeriode.erIPeriode(
                            dagIPeriode!!
                        )
                    }
            }
    }

    fun periodeTilJson(fom: LocalDate, tom: LocalDate): String {
        return "{\"fom\":\"" + fom.format(DateTimeFormatter.ISO_LOCAL_DATE) +
            "\",\"tom\":\"" + tom.format(DateTimeFormatter.ISO_LOCAL_DATE) + "\"}"
    }

    fun datoErInnenforMinMax(dato: LocalDate, min: String?, max: String?): Boolean {
        return !dato.isBefore(mapNullTilLocalDateMIN(min)) && !dato.isAfter(mapNullTilLocalDateMAX(max))
    }

    fun periodeErInnenforMinMax(periode: Periode, min: String?, max: String?): Boolean {
        return datoErInnenforMinMax(periode.fom, min, max) && datoErInnenforMinMax(periode.tom, min, max)
    }

    private fun mapNullTilLocalDateMIN(min: String?): LocalDate {
        return if (min == null) LocalDate.MIN else LocalDate.parse(min, DateTimeFormatter.ISO_LOCAL_DATE)
    }

    private fun mapNullTilLocalDateMAX(max: String?): LocalDate {
        return if (max == null) LocalDate.MAX else LocalDate.parse(max, DateTimeFormatter.ISO_LOCAL_DATE)
    }
}
