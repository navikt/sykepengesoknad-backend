package no.nav.helse.flex.util

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.TIL_SLUTT
import no.nav.helse.flex.testutil.besvarsporsmal

fun Sykepengesoknad.oppsummering(): Sykepengesoknad = besvarsporsmal(TIL_SLUTT, "true")
