package no.nav.helse.flex.soknadsopprettelse.splitt

import no.nav.helse.flex.domain.sykmelding.SykmeldingTilSoknadOpprettelse
import no.nav.helse.flex.domain.sykmelding.Sykmeldingsperiode
import no.nav.helse.flex.util.max
import no.nav.helse.flex.util.min

fun Tidsenhet.delOppISoknadsperioder(sykmeldingDokument: SykmeldingTilSoknadOpprettelse): List<Sykmeldingsperiode> =
    sykmeldingDokument
        .sykmeldingsperioder
        .filter { periode -> periodeTrefferInnenforTidsenhet(periode, this) }
        .map {
            it.copy(
                fom = max(it.fom, this.fom),
                tom = min(it.tom, this.tom),
            )
        }.sortedBy { it.fom }

private fun periodeTrefferInnenforTidsenhet(
    periode: Sykmeldingsperiode,
    tidsenhet: Tidsenhet,
): Boolean = !periode.fom.isAfter(tidsenhet.tom) && !periode.tom.isBefore(tidsenhet.fom)
