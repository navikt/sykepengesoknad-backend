package no.nav.helse.flex.mock

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testutil.besvarsporsmal
import no.nav.helse.flex.util.DatoUtil.periodeTilJson
import no.nav.helse.flex.util.oppsummering
import java.time.LocalDate.now

fun mockUtlandssoknad(): Sykepengesoknad = leggSvarPaSoknad(settOppSoknadOppholdUtland("fnr-7454630"), "NEI")

private fun leggSvarPaSoknad(
    sykepengesoknad: Sykepengesoknad,
    feriesvar: String,
): Sykepengesoknad =
    sykepengesoknad
        .perioder()
        .arbeidsgiver(feriesvar)
        .besvarsporsmal(LAND, "England")
        .oppsummering()

private fun Sykepengesoknad.arbeidsgiver(feriesvarverdi: String): Sykepengesoknad =
    besvarsporsmal(ARBEIDSGIVER, "JA")
        .besvarsporsmal(SYKMELDINGSGRAD, "JA")
        .besvarsporsmal(FERIE, feriesvarverdi)

private fun Sykepengesoknad.perioder(): Sykepengesoknad =
    besvarsporsmal(PERIODEUTLAND, periodeTilJson(now().plusDays(4), now().plusMonths(1)))
