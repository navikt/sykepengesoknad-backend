package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.DatoUtil.periodeTilJson
import java.time.LocalDate.now

fun mockUtlandssoknad(): Sykepengesoknad {
    return leggSvarPaSoknad(settOppSoknadOppholdUtland("fnr-7454630"), "NEI")
}

private fun leggSvarPaSoknad(
    sykepengesoknad: Sykepengesoknad,
    feriesvar: String,
): Sykepengesoknad {
    return sykepengesoknad
        .perioder()
        .arbeidsgiver(feriesvar)
        .besvarsporsmal(LAND, "England")
        .besvarsporsmal(TIL_SLUTT, "Jeg lover å ikke lyve!")
        .besvarsporsmal(BEKREFT_OPPLYSNINGER, "CHECKED")
}

private fun Sykepengesoknad.arbeidsgiver(feriesvarverdi: String): Sykepengesoknad {
    return besvarsporsmal(ARBEIDSGIVER, "JA")
        .besvarsporsmal(SYKMELDINGSGRAD, "JA")
        .besvarsporsmal(FERIE, feriesvarverdi)
}

private fun Sykepengesoknad.perioder(): Sykepengesoknad {
    return besvarsporsmal(PERIODEUTLAND, periodeTilJson(now().plusDays(4), now().plusMonths(1)))
}
