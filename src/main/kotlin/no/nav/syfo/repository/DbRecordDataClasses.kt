package no.nav.syfo.repository

import no.nav.syfo.domain.Arbeidssituasjon
import no.nav.syfo.domain.Avsendertype
import no.nav.syfo.domain.Opprinnelse
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Sykmeldingstype
import no.nav.syfo.domain.Visningskriterie
import java.time.Instant
import java.time.LocalDate

data class SykepengesoknadDbRecord(
    val id: String,
    val sykepengesoknadUuid: String,
    val fnr: String,
    val soknadstype: Soknadstype,
    val status: Soknadstatus,
    val opprettet: Instant?,
    val avbruttDato: LocalDate?,
    val sendtNav: Instant?,
    val korrigerer: String?,
    val korrigertAv: String?,
    val opprinnelse: Opprinnelse,
    val avsendertype: Avsendertype?,
    val sykmeldingId: String?,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val startSykeforlop: LocalDate?,
    val sykmeldingSkrevet: Instant?,
    val sendtArbeidsgiver: Instant?,
    val arbeidsgiverOrgnummer: String?,
    val arbeidsgiverNavn: String?,
    val arbeidssituasjon: Arbeidssituasjon?,
    val egenmeldtSykmelding: Boolean?,
    val merknaderFraSykmelding: String?,
    val avbruttFeilinfo: Boolean?,
)

data class SporsmalDbRecord(
    val id: String,
    val sykepengesoknadId: String,
    val underSporsmalId: String?,
    val tag: String,
    val sporsmalstekst: String?,
    val undertekst: String?,
    val svartype: Svartype,
    val min: String?,
    val max: String?,
    val kriterieForVisningAvUndersporsmal: Visningskriterie?,
)

data class SoknadsperiodeDbRecord(
    val id: String,
    val sykepengesoknadId: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val grad: Int,
    val sykmeldingstype: Sykmeldingstype?
)

data class SvarDbRecord(
    val id: String,
    val sporsmalId: String,
    val verdi: String,
)
