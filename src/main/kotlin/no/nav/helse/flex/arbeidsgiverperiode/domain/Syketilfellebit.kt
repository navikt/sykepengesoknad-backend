package no.nav.helse.flex.arbeidsgiverperiode.domain

import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

fun Set<String>.asString() = this.joinToString(",")

data class Syketilfellebit(
    val id: String? = null,
    val syketilfellebitId: String = UUID.randomUUID().toString(),
    val fnr: String,
    val orgnummer: String?,
    val opprettet: OffsetDateTime,
    val inntruffet: OffsetDateTime,
    val tags: Set<Tag>,
    val ressursId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val korrigererSendtSoknad: String? = null,
    val publisert: Boolean = false,
    val slettet: OffsetDateTime? = null,
)

enum class Tag {
    SYKMELDING,
    NY,
    BEKREFTET,
    SENDT,
    KORRIGERT,
    AVBRUTT,
    UTGAATT,
    PERIODE,
    FULL_AKTIVITET,
    INGEN_AKTIVITET,
    GRADERT_AKTIVITET,
    BEHANDLINGSDAGER,
    BEHANDLINGSDAG,
    ANNET_FRAVAR,
    SYKEPENGESOKNAD,
    FERIE,
    PERMISJON,
    OPPHOLD_UTENFOR_NORGE,
    EGENMELDING,
    FRAVAR_FOR_SYKMELDING,
    PAPIRSYKMELDING,
    ARBEID_GJENNOPPTATT,
    KORRIGERT_ARBEIDSTID,
    UKJENT_AKTIVITET,
    UTDANNING,
    FULLTID,
    DELTID,
    REDUSERT_ARBEIDSGIVERPERIODE,
    REISETILSKUDD,
    AVVENTENDE,
    INNTEKTSMELDING,
    ARBEIDSGIVERPERIODE,
}

fun List<Syketilfellebit>.utenKorrigerteSoknader(): List<Syketilfellebit> {
    val korrigerte = this.mapNotNull { it.korrigererSendtSoknad }.toSet()
    return this.filterNot { korrigerte.contains(it.ressursId) }
}
