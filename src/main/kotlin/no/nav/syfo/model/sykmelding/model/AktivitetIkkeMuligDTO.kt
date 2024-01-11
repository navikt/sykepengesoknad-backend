package no.nav.syfo.model.sykmelding.model

data class AktivitetIkkeMuligDTO(
    val medisinskArsak: MedisinskArsakDTO?,
    val arbeidsrelatertArsak: ArbeidsrelatertArsakDTO?
)
