package no.nav.syfo.sykmelding.kafka.model.sykmelding.arbeidsgiver

data class PrognoseAGDTO(
    val arbeidsforEtterPeriode: Boolean,
    val hensynArbeidsplassen: String?,
)
