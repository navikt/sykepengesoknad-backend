package no.nav.helse.flex.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

val osloZone = ZoneId.of("Europe/Oslo")

fun OffsetDateTime.tilOsloZone(): OffsetDateTime = this.atZoneSameInstant(osloZone).toOffsetDateTime()

fun Instant.tilOsloZone(): OffsetDateTime = this.atZone(osloZone).toOffsetDateTime()

fun Instant.tilLocalDate(): LocalDate = this.tilOsloLocalDateTime().toLocalDate()

fun Instant.tilOsloLocalDateTime(): LocalDateTime = this.tilOsloZone().toLocalDateTime()

fun LocalDateTime.tilOsloInstant(): Instant = this.atZone(osloZone).toInstant()
