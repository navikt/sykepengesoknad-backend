package no.nav.helse.flex.soknadsopprettelse.splitt

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.util.ArrayList

fun ArbeidsgiverSykmelding.splittMellomTyper(): List<ArbeidsgiverSykmelding> {
    val ret = ArrayList<ArbeidsgiverSykmelding>()
    var behandles: ArbeidsgiverSykmelding? = null

    sykmeldingsperioder.sortedBy { it.fom }.forEach { nestePeriode ->
        if (behandles == null) {
            behandles = this.copy(sykmeldingsperioder = listOf(nestePeriode))
            return@forEach
        }

        behandles =
            if (behandles!!.erKompatibel(nestePeriode)) {
                behandles!!.copy(sykmeldingsperioder = listOf(*behandles!!.sykmeldingsperioder.toTypedArray(), nestePeriode))
            } else {
                ret.add(behandles!!)
                this.copy(sykmeldingsperioder = listOf(nestePeriode))
            }
    }
    if (behandles != null) {
        ret.add(behandles!!)
    }

    return ret
}

private fun ArbeidsgiverSykmelding.erKompatibel(nestePeriode: SykmeldingsperiodeAGDTO): Boolean =
    sykmeldingsperioder.last().erGradertEller100Prosent() &&
        nestePeriode.erGradertEller100Prosent() &&
        sykmeldingsperioder.last().tom.plusDays(1) == nestePeriode.fom

private fun SykmeldingsperiodeAGDTO.erAktivitetIkkeMulig(): Boolean = type == PeriodetypeDTO.AKTIVITET_IKKE_MULIG

private fun SykmeldingsperiodeAGDTO.erGradertUtenReisetilskudd(): Boolean {
    val gradertRt = this.gradert?.reisetilskudd ?: false
    return this.type == PeriodetypeDTO.GRADERT && !this.reisetilskudd && !gradertRt
}

private fun SykmeldingsperiodeAGDTO.erGradertEller100Prosent(): Boolean = erAktivitetIkkeMulig() || erGradertUtenReisetilskudd()
