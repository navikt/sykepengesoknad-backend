package no.nav.helse.flex.controller.domain.sykepengesoknad

enum class RSArbeidssituasjon(val navn: String) {
    NAERINGSDRIVENDE("selvstendig næringsdrivende"),
    FISKER("selvstendig næringsdrivende"),
    JORDBRUKER("selvstendig næringsdrivende"),
    FRILANSER("frilanser"),
    ARBEIDSTAKER("arbeidstaker"),
    ARBEIDSLEDIG("arbeidsledig"),
    ANNET("annet"),
    ;

    override fun toString(): String {
        return navn
    }
}
