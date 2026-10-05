package no.nav.helse.flex.controller.domain.sykepengesoknad

data class RSKjentInntektskilde(
    val navn: String,
    val kilde: RSKilde,
    val orgnummer: String,
)

enum class RSKilde {
    INNTEKTSKOMPONENTEN,
    AAAREG,
    SYKMELDING,
}
