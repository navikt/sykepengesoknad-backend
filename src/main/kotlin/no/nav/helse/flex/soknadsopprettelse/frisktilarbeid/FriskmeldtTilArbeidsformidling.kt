package no.nav.helse.flex.soknadsopprettelse.frisktilarbeid

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*

fun settOppSykepengesoknadFriskmeldtTilArbeidsformidling(
    sykepengesoknad: Sykepengesoknad,
    vedtakPeriode: Periode,
): List<Sporsmal> {
    val sisteSoknad = sykepengesoknad.tom!! == vedtakPeriode.tom
    return listOf(
        ansvarserklaringSporsmal(),
        jobbsituasjonenDin(sykepengesoknad.fom!!, sykepengesoknad.tom, sisteSoknad),
        inntektUnderveis(sykepengesoknad.fom, sykepengesoknad.tom),
        ftaReiseTilUtlandet(sykepengesoknad.fom, sykepengesoknad.tom),
        tilSlutt(),
    )
}
