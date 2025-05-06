package no.nav.helse.flex.domain

enum class Arbeidssituasjon(
    val navn: String,
) {
    NAERINGSDRIVENDE("selvstendig næringsdrivende"),
    FRILANSER("frilanser"),
    ARBEIDSTAKER("arbeidstaker"),
    ARBEIDSLEDIG("arbeidsledig"),
    FISKER("selvstendig næringsdrivende"),
    JORDBRUKER("selvstendig næringsdrivende"),
    ANNET("annet"),
    ;

    override fun toString(): String = navn
}
