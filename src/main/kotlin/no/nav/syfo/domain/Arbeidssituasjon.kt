package no.nav.syfo.domain

enum class Arbeidssituasjon(val navn: String) {
    NAERINGSDRIVENDE("selvstendig næringsdrivende"),
    FRILANSER("frilanser"),
    ARBEIDSTAKER("arbeidstaker"),
    ARBEIDSLEDIG("arbeidsledig"),
    ANNET("annet");

    override fun toString(): String {
        return navn
    }
}
