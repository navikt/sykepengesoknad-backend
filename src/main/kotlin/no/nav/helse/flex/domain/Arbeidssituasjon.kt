package no.nav.helse.flex.domain

enum class Arbeidssituasjon(
    val navn: String,
) {
    NAERINGSDRIVENDE("selvstendig næringsdrivende"),
    FRILANSER("frilanser"),
    ARBEIDSTAKER("arbeidstaker"),
    BARNEPASSER("barnepasser"),
    ARBEIDSLEDIG("arbeidsledig"),
    FISKER("fisker"),
    JORDBRUKER("jordbruker"),
    ANNET("annet"),
    ;

    override fun toString(): String = navn

    fun erSelvstendigNaringsdrivende(): Boolean = listOf(NAERINGSDRIVENDE, FISKER, JORDBRUKER, BARNEPASSER).contains(this)
}
