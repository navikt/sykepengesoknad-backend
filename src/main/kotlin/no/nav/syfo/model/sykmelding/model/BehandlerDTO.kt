package no.nav.syfo.model.sykmelding.model

data class BehandlerDTO(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String,
    val aktoerId: String,
    val fnr: String,
    val hpr: String?,
    val her: String?,
    val adresse: AdresseDTO,
    val tlf: String?
)
