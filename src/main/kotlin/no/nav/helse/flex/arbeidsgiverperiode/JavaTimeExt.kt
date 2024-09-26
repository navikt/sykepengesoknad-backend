package no.nav.helse.flex.arbeidsgiverperiode

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

val osloZone = ZoneId.of("Europe/Oslo")

fun LocalDateTime.tilOsloZone(): OffsetDateTime = this.atZone(osloZone).toOffsetDateTime()

fun OffsetDateTime.tilOsloZone(): OffsetDateTime = this.atZoneSameInstant(osloZone).toOffsetDateTime()
