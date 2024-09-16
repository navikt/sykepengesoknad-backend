package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.domain.Sykepengesoknad

fun Sykepengesoknad.erUtenforArbeidsgiverPeriode(andreSoknader: List<Sykepengesoknad>): Boolean {
    val sykmeldtFraArbeidsgiverOrgnr = this.arbeidsgiverOrgnummer

    val arbeidsgiverperiodeFom = this.fom!!
    val arbeidsgiverperiodeTom = this.tom!!

    val avstandFomTilTom = arbeidsgiverperiodeFom.until(arbeidsgiverperiodeTom).days + 1
    return avstandFomTilTom > 16
}
