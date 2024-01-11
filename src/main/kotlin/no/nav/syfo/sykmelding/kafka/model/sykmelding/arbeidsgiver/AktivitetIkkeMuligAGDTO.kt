package no.nav.syfo.sykmelding.kafka.model.sykmelding.arbeidsgiver

import no.nav.syfo.sykmelding.kafka.model.sykmelding.model.ArbeidsrelatertArsakDTO

data class AktivitetIkkeMuligAGDTO(
    val arbeidsrelatertArsak: ArbeidsrelatertArsakDTO?,
)
