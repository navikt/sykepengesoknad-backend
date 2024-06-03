package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.PeriodeMapper
import java.util.*

private fun Sykepengesoknad.hentGyldigePerioder(
    hva: String,
    nar: String,
): List<Periode> {
    return if (this.getSporsmalMedTagOrNull(hva)?.forsteSvar == "JA") {
        getGyldigePeriodesvar(this.getSporsmalMedTag(nar))
    } else {
        Collections.emptyList()
    }
}

private fun getGyldigePeriodesvar(sporsmal: Sporsmal): List<Periode> {
    return sporsmal.svar.asSequence()
        .map { it.verdi }
        .map { PeriodeMapper.jsonTilOptionalPeriode(it) } // TODO: Kan enders?
        .filter { it.isPresent }
        .map { it.get() }
        .filter { periode -> DatoUtil.periodeErInnenforMinMax(periode, sporsmal.min, sporsmal.max) }
        .toList()
        .toList()
}
