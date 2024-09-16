package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.domain.Sykepengesoknad

fun Sykepengesoknad.erUtenforArbeidsgiverPeriode(andreSoknader: List<Sykepengesoknad>): Boolean {
    val sykmeldtFraArbeidsgiverOrgnr = this.arbeidsgiverOrgnummer

    return true
}
