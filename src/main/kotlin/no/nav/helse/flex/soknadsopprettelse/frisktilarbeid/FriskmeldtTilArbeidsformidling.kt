package no.nav.helse.flex.soknadsopprettelse.frisktilarbeid

import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.soknadsopprettelse.sporsmal.*
import java.time.LocalDate

fun settOppSykepengesoknadFriskmeldtTilArbeidsformidling(
    fom: LocalDate,
    tom: LocalDate,
    vedtakPeriode: Periode,
): List<Sporsmal> {
    val sisteSoknad = tom == vedtakPeriode.tom
    return listOf(
        ansvarserklaringSporsmal(),
        jobbsituasjonenDin(fom, tom, sisteSoknad),
        inntektUnderveis(fom, tom),
        ftaReiseTilUtlandet(fom, tom),
        tilSlutt(),
    )
}
