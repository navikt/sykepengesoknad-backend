package no.nav.syfo.client.narmesteleder

import java.io.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class NarmesteLederRelasjon(
    val narmesteLederId: UUID,
    val fnr: String,
    val orgnummer: String,
    val narmesteLederFnr: String,
    val narmesteLederTelefonnummer: String,
    val narmesteLederEpost: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean? = null,
    val skrivetilgang: Boolean = true,
    val tilganger: List<Tilgang> = listOf(
        Tilgang.SYKMELDING,
        Tilgang.SYKEPENGESOKNAD,
        Tilgang.MOTE,
        Tilgang.OPPFOLGINGSPLAN
    ),
    val timestamp: OffsetDateTime,
    val navn: String? = null
) : Serializable

enum class Tilgang {
    SYKMELDING,
    SYKEPENGESOKNAD,
    MOTE,
    OPPFOLGINGSPLAN
}
