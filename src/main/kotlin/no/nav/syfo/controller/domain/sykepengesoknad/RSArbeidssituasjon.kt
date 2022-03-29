package no.nav.syfo.controller.domain.sykepengesoknad

enum class RSArbeidssituasjon(val navn: String) {
    NAERINGSDRIVENDE("selvstendig næringsdrivende"),
    FRILANSER("frilanser"),
    ARBEIDSTAKER("arbeidstaker"),
    ARBEIDSLEDIG("arbeidsledig"),
    ANNET("annet");

    override fun toString(): String {
        return navn
    }
}
