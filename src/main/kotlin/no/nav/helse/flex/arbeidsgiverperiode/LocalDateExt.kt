package no.nav.helse.flex.arbeidsgiverperiode

import java.time.LocalDate

fun LocalDate.isEqualOrAfter(other: LocalDate): Boolean = this == other || this.isAfter(other)

fun LocalDate.isEqualOrBefore(other: LocalDate): Boolean = this == other || this.isBefore(other)
