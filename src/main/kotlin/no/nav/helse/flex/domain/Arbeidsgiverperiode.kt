package no.nav.helse.flex.domain

data class Arbeidsgiverperiode(
    val antallBrukteDager: Int = 0,
    val oppbruktArbeidsgiverperiode: Boolean = false,
    val arbeidsgiverPeriode: Periode? = null
)
