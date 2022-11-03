package no.nav.helse.flex.domain.rest

import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Merknad
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class SoknadMetadata(
    val id: String = UUID.randomUUID().toString(),
    val fnr: String,
    val soknadstype: Soknadstype,
    val startSykeforlop: LocalDate,
    val fom: LocalDate,
    val tom: LocalDate,
    val arbeidssituasjon: Arbeidssituasjon,
    val arbeidsgiverOrgnummer: String? = null,
    val arbeidsgiverNavn: String? = null,
    val sykmeldingId: String,
    val sykmeldingSkrevet: Instant,
    val sykmeldingsperioder: List<Soknadsperiode>,
    val egenmeldtSykmelding: Boolean? = null,
    val merknader: List<Merknad>? = null
)

fun SoknadMetadata.tilSykepengesoknad(): Sykepengesoknad = Sykepengesoknad(
    soknadstype = this.soknadstype,
    id = this.id,
    fnr = this.fnr,
    sykmeldingId = this.sykmeldingId,
    fom = this.fom,
    tom = this.tom,
    status = Soknadstatus.FREMTIDIG,
    opprettet = Instant.now(),
    sporsmal = emptyList(),
    startSykeforlop = this.startSykeforlop,
    sykmeldingSkrevet = this.sykmeldingSkrevet,
    arbeidsgiverOrgnummer = this.arbeidsgiverOrgnummer,
    arbeidsgiverNavn = this.arbeidsgiverNavn,
    soknadPerioder = this.sykmeldingsperioder,
    arbeidssituasjon = this.arbeidssituasjon,
    egenmeldtSykmelding = this.egenmeldtSykmelding,
    merknaderFraSykmelding = this.merknader,
)
