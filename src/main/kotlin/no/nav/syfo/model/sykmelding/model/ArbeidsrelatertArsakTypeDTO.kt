package no.nav.syfo.model.sykmelding.model

enum class ArbeidsrelatertArsakTypeDTO(val codeValue: String, val text: String, val oid: String = "2.16.578.1.12.4.1.1.8132") {
    MANGLENDE_TILRETTELEGGING("1", "Manglende tilrettelegging på arbeidsplassen"),
    ANNET("9", "Annet")
}
