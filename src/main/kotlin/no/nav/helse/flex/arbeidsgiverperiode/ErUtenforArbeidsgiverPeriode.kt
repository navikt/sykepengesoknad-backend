package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.arbeidsgiverperiode.domain.Syketilfellebit
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.konverterTilSykepengesoknadDTO
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad
import java.time.DayOfWeek
import java.time.LocalDate

// TODO:  Erstatt med et kall til flex-syketilfelle og slett duplikatkode.
fun Sykepengesoknad.harDagerNAVSkalBetaleFor(andreSoknader: List<Sykepengesoknad>): Boolean {
    val andreBiter =
        andreSoknader
            .filter { it.arbeidsgiverOrgnummer == this.arbeidsgiverOrgnummer }
            .filter { it.status in listOf(Soknadstatus.SENDT, Soknadstatus.NY) }
            .flatMap { it.tilSyketilfelleBiter() }
            .toMutableList()
            .also { it.addAll(this.tilSyketilfelleBiter()) }

    val arbeidsgiverperiode = beregnArbeidsgiverperiode(andreBiter, this) ?: return false

    val sykepengesoknadTom = this.tom

    if (arbeidsgiverperiode.oppbruktArbeidsgiverperiode && arbeidsgiverperiode.arbeidsgiverPeriode.tom.isBefore(this.tom!!)) {
        // Perioden har dager ut over arbeidsgiverperioden. Sjekk om det er en hverdag blant disse som Nav skal betale for.
        val datoer = lagDatoliste(arbeidsgiverperiode.arbeidsgiverPeriode.tom.plusDays(1), sykepengesoknadTom)
        val hverdagBlantOverskytendeDager = datoer.any { it.erHverdag() }
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

private fun lagDatoliste(
    start: LocalDate,
    end: LocalDate,
): List<LocalDate> =
    generateSequence(start) { date ->
        date.plusDays(1).takeIf { it <= end }
    }.toList()
