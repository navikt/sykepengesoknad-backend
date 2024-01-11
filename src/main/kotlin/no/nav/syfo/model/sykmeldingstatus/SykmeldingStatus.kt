package no.nav.syfo.model.sykmeldingstatus


const val STATUS_APEN = "APEN"
const val STATUS_AVBRUTT = "AVBRUTT"
const val STATUS_UTGATT = "UTGATT"
const val STATUS_SENDT = "SENDT"
const val STATUS_BEKREFTET = "BEKREFTET"
const val STATUS_SLETTET = "SLETTET"

data class ArbeidsgiverStatusDTO(
    val orgnummer: String,
    val juridiskOrgnummer: String? = null,
    val orgNavn: String
)

data class SporsmalOgSvarDTO(
    val tekst: String,
    val shortName: ShortNameDTO,
    val svartype: SvartypeDTO,
    val svar: String
)

enum class ShortNameDTO {
    ARBEIDSSITUASJON, NY_NARMESTE_LEDER, FRAVAER, PERIODE, FORSIKRING, EGENMELDINGSDAGER
}

enum class SvartypeDTO {
    ARBEIDSSITUASJON,
    PERIODER,
    JA_NEI,
    DAGER
}
