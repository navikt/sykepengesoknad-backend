package no.nav.syfo.repository

import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.util.tilOsloZone
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Transactional
@Repository
class SoknadLagrer(
    private val jdbcTemplate: NamedParameterJdbcTemplate
) {

    fun lagreSoknad(soknad: Sykepengesoknad) {

        val normalisertSoknad = soknad.normaliser()
        normalisertSoknad.soknad.lagre()
        normalisertSoknad.perioder.lagrePerioder()
        normalisertSoknad.sporsmal.lagreSporsmal()
        normalisertSoknad.svar.lagreSvar()
    }

    fun SykepengesoknadDbRecord.lagre() {
        val sykepengesoknad = this
        jdbcTemplate.update(
            """INSERT INTO SYKEPENGESOKNAD (ID, SYKEPENGESOKNAD_UUID, SOKNADSTYPE, STATUS, FOM, TOM, OPPRETTET, AVBRUTT_DATO, SYKMELDING_UUID, SENDT_NAV, SENDT_ARBEIDSGIVER, KORRIGERER, KORRIGERT_AV, ARBEIDSGIVER_ORGNUMMER, ARBEIDSGIVER_NAVN, ARBEIDSSITUASJON, START_SYKEFORLOP, SYKMELDING_SKREVET, OPPRINNELSE, FNR, EGENMELDT_SYKMELDING, MERKNADER_FRA_SYKMELDING, AVBRUTT_FEILINFO) VALUES (:id, :uuid, :soknadstype, :status, :fom, :tom, :opprettet, :avbrutt, :sykmeldingUuid, :sendtNav, :sendtArbeidsgiver, :korrigerer, :korrigertAv, :arbeidsgiverOrgnummer, :arbeidsgiverNavn, :arbeidssituasjon, :startSykeforlop, :sykmeldingSkrevet, :opprinnelse, :fnr, :egenmeldtSykmelding, :merknaderFraSykmelding, :avbruttFeilinfo)""",

            MapSqlParameterSource()
                .addValue("id", sykepengesoknad.id)
                .addValue("uuid", sykepengesoknad.sykepengesoknadUuid)
                .addValue("soknadstype", sykepengesoknad.soknadstype.name)
                .addValue("status", sykepengesoknad.status.name)
                .addValue("fom", sykepengesoknad.fom)
                .addValue("tom", sykepengesoknad.tom)
                .addValue("opprettet", sykepengesoknad.opprettet?.tilOsloZone())
                .addValue("avbrutt", sykepengesoknad.avbruttDato)
                .addValue("sendtNav", sykepengesoknad.sendtNav?.tilOsloZone())
                .addValue("sendtArbeidsgiver", sykepengesoknad.sendtArbeidsgiver?.tilOsloZone())
                .addValue("sykmeldingUuid", sykepengesoknad.sykmeldingId)
                .addValue("korrigerer", sykepengesoknad.korrigerer)
                .addValue("korrigertAv", sykepengesoknad.korrigertAv)
                .addValue("arbeidsgiverOrgnummer", sykepengesoknad.arbeidsgiverOrgnummer)
                .addValue("arbeidsgiverNavn", sykepengesoknad.arbeidsgiverNavn)
                .addValue("arbeidssituasjon", sykepengesoknad.arbeidssituasjon?.name)
                .addValue("startSykeforlop", sykepengesoknad.startSykeforlop)
                .addValue("sykmeldingSkrevet", sykepengesoknad.sykmeldingSkrevet?.tilOsloZone())
                .addValue("opprinnelse", sykepengesoknad.opprinnelse.name)
                .addValue("fnr", sykepengesoknad.fnr)
                .addValue("egenmeldtSykmelding", sykepengesoknad.egenmeldtSykmelding)
                .addValue("avbruttFeilinfo", sykepengesoknad.avbruttFeilinfo)
                .addValue("merknaderFraSykmelding", sykepengesoknad.merknaderFraSykmelding)
        )
    }

    private fun List<SoknadsperiodeDbRecord>.lagrePerioder() {
        if (this.isEmpty()) {
            return
        }
        this.forEach {
            jdbcTemplate.update(
                "INSERT INTO SOKNADPERIODE (ID, SYKEPENGESOKNAD_ID, FOM, TOM, GRAD, SYKMELDINGSTYPE) " + "VALUES (:id, :sykepengesoknadId, :fom, :tom, :grad, :sykmeldingstype)",

                MapSqlParameterSource()
                    .addValue("id", it.id)
                    .addValue("sykepengesoknadId", it.sykepengesoknadId)
                    .addValue("fom", it.fom)
                    .addValue("tom", it.tom)
                    .addValue("grad", it.grad)
                    .addValue("sykmeldingstype", it.sykmeldingstype?.name)
            )
        }
    }

    private fun List<SporsmalDbRecord>.lagreSporsmal() {
        if (this.isEmpty()) {
            return
        }
        val sql =
            """
INSERT INTO SPORSMAL(ID, SYKEPENGESOKNAD_ID, UNDER_SPORSMAL_ID, TEKST, UNDERTEKST, TAG, SVARTYPE, MIN, MAX, KRITERIE_FOR_VISNING) 
VALUES (:id, :sykepengesoknadId, :underSporsmalId, :tekst, :undertekst, :tag, :svartype, :min, :max, :kriterie)"""
        jdbcTemplate.batchUpdate(
            sql,
            this
                .map {
                    MapSqlParameterSource()
                        .addValue("id", it.id)
                        .addValue("sykepengesoknadId", it.sykepengesoknadId)
                        .addValue("underSporsmalId", it.underSporsmalId)
                        .addValue("tekst", it.sporsmalstekst)
                        .addValue("undertekst", it.undertekst)
                        .addValue("tag", it.tag)
                        .addValue("svartype", it.svartype.name)
                        .addValue("min", it.min)
                        .addValue("max", it.max)
                        .addValue("kriterie", it.kriterieForVisningAvUndersporsmal?.name)
                }
                .toTypedArray()
        )
    }

    private fun List<SvarDbRecord>.lagreSvar() {
        if (this.isEmpty()) {
            return
        }
        val sql =
            """
INSERT INTO SVAR (ID, SPORSMAL_ID, VERDI) VALUES (:id, :sporsmalId, :verdi)"""
        jdbcTemplate.batchUpdate(
            sql,
            this
                .map {
                    MapSqlParameterSource()
                        .addValue("id", it.id)
                        .addValue("sporsmalId", it.sporsmalId)
                        .addValue("verdi", it.verdi)
                }
                .toTypedArray()
        )
    }
}
