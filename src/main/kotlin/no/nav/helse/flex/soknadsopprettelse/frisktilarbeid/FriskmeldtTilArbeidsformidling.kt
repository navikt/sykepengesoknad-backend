package no.nav.helse.flex.soknadsopprettelse.frisktilarbeid

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.soknadsopprettelse.SettOppSoknadOptions
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*

fun settOppSykepengesoknadFriskmeldtTilArbeidsformidling(opts: SettOppSoknadOptions): List<Sporsmal> {
    val (sykepengesoknad) = opts

    return listOf(
        ansvarserklaringSporsmal(),
        jobbsituasjonenDin(sykepengesoknad.fom!!, sykepengesoknad.tom!!),
        inntektUnderveis(sykepengesoknad.fom, sykepengesoknad.tom),
        ftaReiseTilUtlandet(sykepengesoknad.fom, sykepengesoknad.tom),
        tilSlutt(),
    )
}
