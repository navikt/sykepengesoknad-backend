package no.nav.helse.flex.domain

enum class Soknadstype(val visesPåDineSykmeldte: Boolean) {
    SELVSTENDIGE_OG_FRILANSERE(visesPåDineSykmeldte = false),
    OPPHOLD_UTLAND(visesPåDineSykmeldte = false),
    ARBEIDSLEDIG(visesPåDineSykmeldte = false),
    ANNET_ARBEIDSFORHOLD(visesPåDineSykmeldte = false),
    ARBEIDSTAKERE(visesPåDineSykmeldte = true),
    BEHANDLINGSDAGER(visesPåDineSykmeldte = true),
    REISETILSKUDD(visesPåDineSykmeldte = false),
    GRADERT_REISETILSKUDD(visesPåDineSykmeldte = true)
}
