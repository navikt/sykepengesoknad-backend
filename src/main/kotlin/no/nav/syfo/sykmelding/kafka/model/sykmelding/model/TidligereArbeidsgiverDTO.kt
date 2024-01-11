package no.nav.syfo.sykmelding.kafka.model.sykmelding.model

data class TidligereArbeidsgiverDTO(
    val orgNavn: String,
    val orgnummer: String,
    val sykmeldingsId: String,
)
