package no.nav.helse.flex.soknadsopprettelse.splitt

import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.domain.sykmelding.SykmeldingTilSoknadOpprettelse
import no.nav.helse.flex.domain.sykmelding.Sykmeldingsperiode

fun SykmeldingTilSoknadOpprettelse.splittMellomTyper(): List<SykmeldingTilSoknadOpprettelse> {
    val ret = ArrayList<SykmeldingTilSoknadOpprettelse>()
    var behandles: SykmeldingTilSoknadOpprettelse? = null

    sykmeldingsperioder.sortedBy { it.fom }.forEach { nestePeriode ->
        if (behandles == null) {
            behandles = this.copy(sykmeldingsperioder = listOf(nestePeriode))
            return@forEach
        }

        behandles =
            if (behandles.erKompatibel(nestePeriode)) {
                behandles.copy(sykmeldingsperioder = listOf(*behandles.sykmeldingsperioder.toTypedArray(), nestePeriode))
            } else {
                ret.add(behandles)
                this.copy(sykmeldingsperioder = listOf(nestePeriode))
            }
    }
    if (behandles != null) {
        ret.add(behandles)
    }

    return ret
}

private fun SykmeldingTilSoknadOpprettelse.erKompatibel(nestePeriode: Sykmeldingsperiode): Boolean =
    sykmeldingsperioder.last().erGradertEller100Prosent() &&
        nestePeriode.erGradertEller100Prosent() &&
        sykmeldingsperioder.last().tom.plusDays(1) == nestePeriode.fom

private fun Sykmeldingsperiode.erAktivitetIkkeMulig(): Boolean = type == Sykmeldingstype.AKTIVITET_IKKE_MULIG

private fun Sykmeldingsperiode.erGradertUtenReisetilskudd(): Boolean {
    val gradertRt = gradert?.reisetilskudd ?: false
    return type == Sykmeldingstype.GRADERT && !this.reisetilskudd && !gradertRt
}

private fun Sykmeldingsperiode.erGradertEller100Prosent(): Boolean = erAktivitetIkkeMulig() || erGradertUtenReisetilskudd()
