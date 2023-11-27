package no.nav.helse.flex.repository

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Avsendertype
import no.nav.helse.flex.domain.Opprinnelse
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Sykmeldingstype
import no.nav.helse.flex.domain.Visningskriterie
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant
import java.time.LocalDate

@Table("sykepengesoknad")
data class SykepengesoknadDbRecord(
    @Id
    val id: String? = null,
    val sykepengesoknadUuid: String,
    val fnr: String,
    val soknadstype: Soknadstype,
    val status: Soknadstatus,
    val opprettet: Instant?,
    val avbruttDato: LocalDate?,
    val sendtNav: Instant?,
    val sendtArbeidsgiver: Instant?,
    val sendt: Instant?,
    val korrigerer: String?,
    val korrigertAv: String?,
    val opprinnelse: Opprinnelse,
    val avsendertype: Avsendertype?,
    val sykmeldingUuid: String?,
    val fom: LocalDate?,
    val tom: LocalDate?,
    val startSykeforlop: LocalDate?,
    val sykmeldingSkrevet: Instant?,
    val sykmeldingSignaturDato: Instant?,
    val arbeidsgiverOrgnummer: String?,
    val arbeidsgiverNavn: String?,
    val arbeidssituasjon: Arbeidssituasjon?,
    val egenmeldtSykmelding: Boolean?,
    val merknaderFraSykmelding: String?,
    val avbruttFeilinfo: Boolean?,
    val opprettetAvInntektsmelding: Boolean = false,
    val utenlandskSykmelding: Boolean,
    val egenmeldingsdagerFraSykmelding: String? = null,
    val forstegangssoknad: Boolean?,
    val tidligereArbeidsgiverOrgnummer: String? = null
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
    val kriterieForVisningAvUndersporsmal: Visningskriterie?
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
    val verdi: String
)
