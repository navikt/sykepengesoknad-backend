package no.nav.helse.flex.soknadsopprettelse.splitt

import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO
import java.util.ArrayList

fun ArbeidsgiverSykmelding.splittMellomTyper(): List<ArbeidsgiverSykmelding> {
    val ret = ArrayList<ArbeidsgiverSykmelding>()
    var behandles: ArbeidsgiverSykmelding? = null

    this.sykmeldingsperioder.sortedBy { it.fom }.forEach {
        if (behandles == null) {
            behandles = this.copy(sykmeldingsperioder = listOf(it))
            return@forEach
        }

        behandles = if (behandles!!.erKompatibel(it)) {
            behandles!!.copy(sykmeldingsperioder = listOf(*behandles!!.sykmeldingsperioder.toTypedArray(), it))
        } else {
            ret.add(behandles!!)
            this.copy(sykmeldingsperioder = listOf(it))
        }
    }
    if (behandles != null) {
        ret.add(behandles!!)
    }

    return ret
}

private fun ArbeidsgiverSykmelding.erKompatibel(sykmeldingsperiodeDTO: SykmeldingsperiodeAGDTO): Boolean {
    return Pair(sykmeldingsperiodeDTO, sykmeldingsperioder.last()).erKompatible()
}

private fun Pair<SykmeldingsperiodeAGDTO, SykmeldingsperiodeAGDTO>.erKompatible(): Boolean {
    return first.erGradertEller100Prosent() && second.erGradertEller100Prosent()
}

private fun SykmeldingsperiodeAGDTO.erAktivitetIkkeMulig(): Boolean {
    return type == PeriodetypeDTO.AKTIVITET_IKKE_MULIG
}

private fun SykmeldingsperiodeAGDTO.erGradertUtenReisetilskudd(): Boolean {
    val gradertRt = this.gradert?.reisetilskudd ?: false
    return this.type == PeriodetypeDTO.GRADERT && !this.reisetilskudd && !gradertRt
}

private fun SykmeldingsperiodeAGDTO.erGradertEller100Prosent(): Boolean {
    return erAktivitetIkkeMulig() || erGradertUtenReisetilskudd()
}
