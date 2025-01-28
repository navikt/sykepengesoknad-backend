package no.nav.helse.flex.domain

enum class Soknadstype(val visesPåDineSykmeldte: Boolean) {
    SELVSTENDIGE_OG_FRILANSERE(visesPåDineSykmeldte = false),
    OPPHOLD_UTLAND(visesPåDineSykmeldte = false),
    ARBEIDSTAKERE(visesPåDineSykmeldte = true),
    ARBEIDSLEDIG(visesPåDineSykmeldte = false),
    BEHANDLINGSDAGER(visesPåDineSykmeldte = true),
    ANNET_ARBEIDSFORHOLD(visesPåDineSykmeldte = false),
    REISETILSKUDD(visesPåDineSykmeldte = false),
    GRADERT_REISETILSKUDD(visesPåDineSykmeldte = true),
    FRISKMELDT_TIL_ARBEIDSFORMIDLING(visesPåDineSykmeldte = false),
}
