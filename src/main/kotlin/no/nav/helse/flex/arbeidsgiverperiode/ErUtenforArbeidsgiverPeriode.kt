package no.nav.helse.flex.arbeidsgiverperiode

import no.nav.helse.flex.arbeidsgiverperiode.domain.Syketilfellebit
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.konverterTilSykepengesoknadDTO
import no.nav.helse.flex.domain.mapper.sporsmalprossesering.hentSoknadsPerioderMedFaktiskGrad

fun Sykepengesoknad.erUtenforArbeidsgiverPeriode(andreSoknader: List<Sykepengesoknad>): Boolean {
    val andreBiter =
        andreSoknader
            .filter { it.arbeidsgiverOrgnummer == this.arbeidsgiverOrgnummer }
            .filter { it.status in listOf(Soknadstatus.SENDT, Soknadstatus.NY) }
            .flatMap { it.tilSyketilfelleBiter() }
            .toMutableList()
            .also { it.addAll(this.tilSyketilfelleBiter()) }

    val beregnArbeidsgiverperiode = beregnArbeidsgiverperiode(andreBiter, this)
    val arbeidsgiverperiode = beregnArbeidsgiverperiode ?: return false
    return arbeidsgiverperiode.oppbruktArbeidsgiverperiode && arbeidsgiverperiode.arbeidsgiverPeriode.tom.isBefore(this.tom!!)
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
