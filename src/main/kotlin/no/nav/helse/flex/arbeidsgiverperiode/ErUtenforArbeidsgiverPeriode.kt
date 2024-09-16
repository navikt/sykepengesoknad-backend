package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Sykepengesoknad
import java.time.LocalDate

fun Sykepengesoknad.erUtenforArbeidsgiverPeriode(andreSoknader: List<Sykepengesoknad>): Boolean {
    val sykmeldtFraArbeidsgiverOrgnr = this.arbeidsgiverOrgnummer

    val alleDager = andreSoknader
        .filter { it.arbeidsgiverOrgnummer == sykmeldtFraArbeidsgiverOrgnr }
        .skapDager()

    val arbeidsgiverperioder = alleDager.beregnArbeidsgiverperioder()

    return !arbeidsgiverperioder.any { it.overlapper(this.fom, this.tom) }

}

private fun List<DagOgType>.beregnArbeidsgiverperioder(): List<Periode> {
    return listOf(Periode(LocalDate.now(), LocalDate.now().plusDays(1)))
}


enum class DagType{
    ARBEIDSDAG,  FERIEDAG, PERMISJONSDAG, SYKEDAG, EGENMELDING}

data class DagOgType(
    val dag: LocalDate,
    val type: DagType

)

fun List<Sykepengesoknad>.skapDager(): List<DagOgType> {

    return listOf(DagOgType(LocalDate.now(), DagType.FERIEDAG), DagOgType(LocalDate.now().plusDays(1), DagType.ARBEIDSDAG))

}
