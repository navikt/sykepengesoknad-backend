package no.nav.syfo.domain

import java.io.Serializable
import java.time.LocalDate

data class Soknadsperiode(
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val sykmeldingstype: Sykmeldingstype?
) : Serializable
