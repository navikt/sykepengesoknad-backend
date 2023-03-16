package no.nav.helse.flex.soknadsopprettelse.splitt

import no.nav.helse.flex.util.max
import no.nav.helse.flex.util.min
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO

fun Tidsenhet.delOppISoknadsperioder(sykmeldingDokument: ArbeidsgiverSykmelding): List<SykmeldingsperiodeAGDTO> {
    return sykmeldingDokument
        .sykmeldingsperioder
        .filter { periode -> periodeTrefferInnenforTidsenhet(periode, this) }
        .map {
            it.copy(
                fom = max(it.fom, this.fom),
                tom = min(it.tom, this.tom)
            )
        }
        .sortedBy { it.fom }
}

private fun periodeTrefferInnenforTidsenhet(periode: SykmeldingsperiodeAGDTO, tidsenhet: Tidsenhet): Boolean {
    return !periode.fom.isAfter(tidsenhet.tom) && !periode.tom.isBefore(tidsenhet.fom)
}
