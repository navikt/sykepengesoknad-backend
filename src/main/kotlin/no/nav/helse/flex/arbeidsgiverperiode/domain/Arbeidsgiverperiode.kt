package no.nav.helse.flex.arbeidsgiverperiode.domain

data class Arbeidsgiverperiode(
    val antallBrukteDager: Int,
    val oppbruktArbeidsgiverperiode: Boolean,
    val arbeidsgiverPeriode: PeriodeDTO,
)
