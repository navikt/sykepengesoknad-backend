package no.nav.helse.flex.oppdatersporsmal.soknad.muteringer

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.JOBBET_DU_100_PROSENT

fun Sykepengesoknad.jobbaDuHundreGate(): Sykepengesoknad {
    return this.copy(sporsmal = sporsmal.filterNot { it.tag.contains(JOBBET_DU_100_PROSENT) })
}
