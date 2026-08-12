package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Periode
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
