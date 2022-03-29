package no.nav.syfo.domain.rest

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Merknad
import no.nav.syfo.domain.Soknadsperiode
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class SoknadMetadata(
    val id: String = UUID.randomUUID().toString(),
    val fnr: String,
    val status: Soknadstatus,
    val soknadstype: Soknadstype,
    val startSykeforlop: LocalDate,
    val fom: LocalDate,
    val tom: LocalDate,
    val arbeidssituasjon: Arbeidssituasjon,
    val arbeidsgiverOrgnummer: String? = null,
    val arbeidsgiverNavn: String? = null,
    val sykmeldingId: String,
    val sykmeldingSkrevet: LocalDateTime,
    val sykmeldingsperioder: List<Soknadsperiode>,
    val egenmeldtSykmelding: Boolean? = null,
    val merknader: List<Merknad>? = null
)
