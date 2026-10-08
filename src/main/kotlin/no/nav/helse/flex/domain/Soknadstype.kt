package no.nav.helse.flex.domain

enum class Soknadstype(
    val kreverFomOgTom: Boolean = true,
    val kreverArbeidssituasjon: Boolean = true,
) {
    ARBEIDSTAKERE,
    SELVSTENDIGE_OG_FRILANSERE,
    ARBEIDSLEDIG,
    ANNET_ARBEIDSFORHOLD,
    BEHANDLINGSDAGER,
    REISETILSKUDD,
    GRADERT_REISETILSKUDD,
    OPPHOLD_UTLAND(
        kreverFomOgTom = false,
        kreverArbeidssituasjon = false,
    ),
    FRISKMELDT_TIL_ARBEIDSFORMIDLING(
        kreverArbeidssituasjon = false,
    ),
}
