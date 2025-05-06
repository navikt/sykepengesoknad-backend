package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.arbeidsgiverperiode.domain.Syketilfellebit
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.konverterTilSykepengesoknadDTO
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import java.time.DayOfWeek
import java.time.LocalDate

fun Sykepengesoknad.harDagerNAVSkalBetaleFor(andreSoknader: List<Sykepengesoknad>): Boolean {
    val andreBiter =
        andreSoknader
            .filter { it.arbeidsgiverOrgnummer == this.arbeidsgiverOrgnummer }
            .filter { it.status in listOf(Soknadstatus.SENDT, Soknadstatus.NY) }
            .flatMap { it.tilSyketilfelleBiter() }
            .toMutableList()
            .also { it.addAll(this.tilSyketilfelleBiter()) }

    val beregnArbeidsgiverperiode = beregnArbeidsgiverperiode(andreBiter, this)
    val arbeidsgiverperiode = beregnArbeidsgiverperiode ?: return false

    val sykepengesoknadTom = this.tom

    if (arbeidsgiverperiode.oppbruktArbeidsgiverperiode && arbeidsgiverperiode.arbeidsgiverPeriode.tom.isBefore(this.tom!!)) {
        // det finnes overskytende dager, sjekk om det er en hverdag blant disse som nav skal betale for
        val range = datoListe(arbeidsgiverperiode.arbeidsgiverPeriode.tom.plusDays(1), sykepengesoknadTom!!)
        val hverdagBlantOverskytendeDager = range.any { it.erHverdag() }
        return hverdagBlantOverskytendeDager
    } else {
        return false
    }
}

private fun Sykepengesoknad.tilSyketilfelleBiter(): List<Syketilfellebit> {
    val sykepengesoknadDTO =
        konverterTilSykepengesoknadDTO(
            sykepengesoknad = this,
            mottaker = null,
            erEttersending = false,
            soknadsperioder = hentSoknadsPerioderMedFaktiskGrad(this).first,
        )

    return sykepengesoknadDTO.mapSoknadTilBiter()
}

private fun LocalDate.erHverdag(): Boolean = this.dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY

private fun datoListe(
    start: LocalDate,
    end: LocalDate,
): List<LocalDate> =
    generateSequence(start) { date ->
        date.plusDays(1).takeIf { it <= end }
    }.toList()
