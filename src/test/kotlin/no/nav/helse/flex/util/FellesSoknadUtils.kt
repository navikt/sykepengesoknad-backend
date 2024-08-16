package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.TIL_SLUTT
import no.nav.helse.flex.testutil.besvarsporsmal

fun Sykepengesoknad.oppsummering(): Sykepengesoknad {
    return besvarsporsmal(TIL_SLUTT, "true").besvarsporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
}
