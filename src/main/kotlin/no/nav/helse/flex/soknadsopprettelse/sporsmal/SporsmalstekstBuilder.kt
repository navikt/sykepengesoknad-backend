package no.nav.helse.flex.soknadsopprettelse.sporsmal

import no.nav.helse.flex.util.DatoUtil.formatterPeriode
import java.time.LocalDate

fun byggSporsmalstekstMedPeriode(
    fom: LocalDate,
    tom: LocalDate,
    restAvTekst: String,
): String = "I perioden ${formatterPeriode(fom, tom)} $restAvTekst"

fun byggSporsmalstekstMedPeriodeMidt(
    forsteDel: String,
    fom: LocalDate,
    tom: LocalDate,
    sisteDelAvTekst: String,
): String = "$forsteDel ${formatterPeriode(fom, tom)} $sisteDelAvTekst"
