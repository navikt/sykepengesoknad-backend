package no.nav.helse.flex.arbeidsgiverperiode.domain

import java.time.LocalDate

class Syketilfelledag(
    val dag: LocalDate,
    val prioritertSyketilfellebit: Syketilfellebit?,
    val syketilfellebiter: List<Syketilfellebit>,
)
