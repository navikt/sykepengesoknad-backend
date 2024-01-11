package no.nav.syfo.model.sykmelding.model

enum class MedisinskArsakTypeDTO(val codeValue: String, val text: String, val oid: String = "2.16.578.1.12.4.1.1.8133") {
    TILSTAND_HINDRER_AKTIVITET("1", "Helsetilstanden hindrer pasienten i å være i aktivitet"),
    AKTIVITET_FORVERRER_TILSTAND("2", "Aktivitet vil forverre helsetilstanden"),
    AKTIVITET_FORHINDRER_BEDRING("3", "Aktivitet vil hindre/forsinke bedring av helsetilstanden"),
    ANNET("9", "Annet")
}
