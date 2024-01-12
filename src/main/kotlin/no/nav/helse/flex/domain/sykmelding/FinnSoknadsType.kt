package no.nav.helse.flex.domain.sykmelding

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Soknadstype
import no.nav.syfo.model.sykmelding.arbeidsgiver.SykmeldingsperiodeAGDTO
import no.nav.syfo.model.sykmelding.model.PeriodetypeDTO

fun finnSoknadsType(
    arbeidssituasjon: Arbeidssituasjon,
    perioderFraSykmeldingen: List<SykmeldingsperiodeAGDTO>,
): Soknadstype {
    if (perioderFraSykmeldingen.any { it.type === PeriodetypeDTO.BEHANDLINGSDAGER }) {
        return Soknadstype.BEHANDLINGSDAGER
    }

    if (perioderFraSykmeldingen.all { it.type === PeriodetypeDTO.REISETILSKUDD }) {
        return Soknadstype.REISETILSKUDD
    }

    if (perioderFraSykmeldingen.any { it.gradert?.reisetilskudd == true }) {
        return Soknadstype.GRADERT_REISETILSKUDD
    }

    return when (arbeidssituasjon) {
        Arbeidssituasjon.ARBEIDSTAKER -> Soknadstype.ARBEIDSTAKERE
        Arbeidssituasjon.NAERINGSDRIVENDE,
        Arbeidssituasjon.FRILANSER,
        Arbeidssituasjon.FISKER,
        Arbeidssituasjon.JORDBRUKER,
        -> Soknadstype.SELVSTENDIGE_OG_FRILANSERE
        Arbeidssituasjon.ARBEIDSLEDIG -> Soknadstype.ARBEIDSLEDIG
        Arbeidssituasjon.ANNET -> Soknadstype.ANNET_ARBEIDSFORHOLD
    }
}
