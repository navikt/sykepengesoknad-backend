package no.nav.syfo.migrering

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.soknadsopprettelse.PERMITTERT_NAA_NAR
import no.nav.syfo.util.DatoUtil
import java.time.LocalDate
import java.time.format.DateTimeFormatter

fun Sykepengesoknad.fixGrenseverdier(): Sykepengesoknad {
    if (harSporsmalMedFeilGrenseverdi()) {
        val oppdatertSpm = hentSpmMedNyGrenseverdi()
        return this.replaceSporsmal(oppdatertSpm)
    }
    return this
}

private fun Sykepengesoknad.harSporsmalMedFeilGrenseverdi(): Boolean {
    val sporsmal = this.getSporsmalMedTagOrNull(PERMITTERT_NAA_NAR) ?: return false

    return !DatoUtil.datoErInnenforMinMax(LocalDate.of(2020, 2, 1), sporsmal.min, sporsmal.max)
}

private fun Sykepengesoknad.hentSpmMedNyGrenseverdi(): Sporsmal {
    return this.getSporsmalMedTag(PERMITTERT_NAA_NAR)
        .copy(min = LocalDate.of(2020, 2, 1).format(DateTimeFormatter.ISO_LOCAL_DATE))
}
