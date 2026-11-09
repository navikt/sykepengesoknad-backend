package no.nav.helse.flex.forskuttering.domain

import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Table("forskuttering_esyfo")
data class ForskutteringEsyfo(
    @Id val id: String? = null,
    val oppdatert: Instant,
    val timestamp: Instant,
    val narmesteLederId: UUID,
    val orgnummer: String,
    val brukerFnr: String,
    val aktivFom: LocalDate,
    val aktivTom: LocalDate?,
    val arbeidsgiverForskutterer: Boolean?,
)
