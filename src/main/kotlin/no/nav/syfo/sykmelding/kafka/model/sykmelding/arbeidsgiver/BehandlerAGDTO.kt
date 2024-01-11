package no.nav.syfo.sykmelding.kafka.model.sykmelding.arbeidsgiver

import no.nav.syfo.sykmelding.kafka.model.sykmelding.model.AdresseDTO

data class BehandlerAGDTO(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val hpr: String?,
    val adresse: AdresseDTO,
    val tlf: String?,
)
