package no.nav.helse.flex.repository

import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.util.tilOsloZone
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

interface SoknadLagrer {
    fun lagreSoknad(soknad: Sykepengesoknad)

    fun lagreSporsmalOgSvarFraSoknad(soknad: Sykepengesoknad)

    fun List<SykepengesoknadDbRecord>.lagre()

    fun List<SoknadsperiodeDbRecord>.lagrePerioder()

    fun List<SporsmalDbRecord>.lagreSporsmal()

    fun List<SvarDbRecord>.lagreSvar()
}

@Transactional
@Repository
class SoknadLagrerImpl(
    private val jdbcTemplate: NamedParameterJdbcTemplate,
) : SoknadLagrer {
    override fun lagreSoknad(soknad: Sykepengesoknad) {
        val normalisertSoknad = soknad.normaliser()
        listOf(normalisertSoknad.soknad).lagre()
        normalisertSoknad.perioder.lagrePerioder()
        normalisertSoknad.sporsmal.lagreSporsmal()
        normalisertSoknad.svar.lagreSvar()
    }

    override fun lagreSporsmalOgSvarFraSoknad(soknad: Sykepengesoknad) {
        val normalisertSoknad = soknad.normaliser()
        normalisertSoknad.sporsmal.lagreSporsmal()
        normalisertSoknad.svar.lagreSvar()
    }

    override fun List<SykepengesoknadDbRecord>.lagre() {
        if (this.isEmpty()) {
            return
        }

        val sql = """
INSERT INTO SYKEPENGESOKNAD (ID, SYKEPENGESOKNAD_UUID, SOKNADSTYPE, STATUS, FOM, TOM, OPPRETTET, AVBRUTT_DATO, SYKMELDING_UUID, SENDT_NAV, SENDT_ARBEIDSGIVER, KORRIGERER, KORRIGERT_AV, ARBEIDSGIVER_ORGNUMMER, ARBEIDSGIVER_NAVN, ARBEIDSSITUASJON, START_SYKEFORLOP, SYKMELDING_SKREVET, SYKMELDING_SIGNATUR_DATO, OPPRINNELSE, FNR, EGENMELDT_SYKMELDING, MERKNADER_FRA_SYKMELDING, OPPRETTET_AV_INNTEKTSMELDING, UTENLANDSK_SYKMELDING, EGENMELDINGSDAGER_FRA_SYKMELDING, FORSTEGANGSSOKNAD, TIDLIGERE_ARBEIDSGIVER_ORGNUMMER, AKTIVERT_DATO, FISKER_BLAD, ARBEIDSFORHOLD_FRA_AAREG, FRISK_TIL_ARBEID_VEDTAK_ID, SELVSTENDIG_NARINGSDRIVENDE) 
VALUES (:id, :uuid, :soknadstype, :status, :fom, :tom, :opprettet, :avbrutt, :sykmeldingUuid, :sendtNav, :sendtArbeidsgiver, :korrigerer, :korrigertAv, :arbeidsgiverOrgnummer, :arbeidsgiverNavn, :arbeidssituasjon, :startSykeforlop, :sykmeldingSkrevet, :sykmeldingSignaturDato, :opprinnelse, :fnr, :egenmeldtSykmelding, :merknaderFraSykmelding, :opprettetAvInntektsmelding, :utenlandskSykmelding, :egenmeldingsdagerFraSykmelding, :forstegangssoknad, :tidligereArbeidsgiverOrgnummer, :aktivertDato, :fiskerBlad, :arbeidsforholdFraAareg, :friskTilArbeidVedtakId, :selvstendigNaringsdrivende) 
ON CONFLICT ON CONSTRAINT sykepengesoknad_pkey DO NOTHING
"""
        jdbcTemplate.batchUpdate(
            sql,
            this
                .map {
                    val sykepengesoknad = it
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
                        .addValue("sykmeldingUuid", sykepengesoknad.sykmeldingUuid)
                        .addValue("korrigerer", sykepengesoknad.korrigerer)
                        .addValue("korrigertAv", sykepengesoknad.korrigertAv)
                        .addValue("arbeidsgiverOrgnummer", sykepengesoknad.arbeidsgiverOrgnummer)
                        .addValue("arbeidsgiverNavn", sykepengesoknad.arbeidsgiverNavn)
                        .addValue("arbeidssituasjon", sykepengesoknad.arbeidssituasjon?.name)
                        .addValue("startSykeforlop", sykepengesoknad.startSykeforlop)
                        .addValue("sykmeldingSkrevet", sykepengesoknad.sykmeldingSkrevet?.tilOsloZone())
                        .addValue("sykmeldingSignaturDato", sykepengesoknad.sykmeldingSignaturDato?.tilOsloZone())
                        .addValue("opprinnelse", sykepengesoknad.opprinnelse.name)
                        .addValue("fnr", sykepengesoknad.fnr)
                        .addValue("egenmeldtSykmelding", sykepengesoknad.egenmeldtSykmelding)
                        .addValue("merknaderFraSykmelding", sykepengesoknad.merknaderFraSykmelding)
                        .addValue("opprettetAvInntektsmelding", sykepengesoknad.opprettetAvInntektsmelding)
                        .addValue("utenlandskSykmelding", sykepengesoknad.utenlandskSykmelding)
                        .addValue("egenmeldingsdagerFraSykmelding", sykepengesoknad.egenmeldingsdagerFraSykmelding)
                        .addValue("forstegangssoknad", sykepengesoknad.forstegangssoknad)
                        .addValue("tidligereArbeidsgiverOrgnummer", sykepengesoknad.tidligereArbeidsgiverOrgnummer)
                        .addValue("aktivertDato", sykepengesoknad.aktivertDato)
                        .addValue("fiskerBlad", sykepengesoknad.fiskerBlad?.name)
                        .addValue("arbeidsforholdFraAareg", sykepengesoknad.arbeidsforholdFraAareg)
                        .addValue("friskTilArbeidVedtakId", sykepengesoknad.friskTilArbeidVedtakId)
                        .addValue("selvstendigNaringsdrivende", sykepengesoknad.selvstendigNaringsdrivende)
                }.toTypedArray(),
        )
    }

    override fun List<SoknadsperiodeDbRecord>.lagrePerioder() {
        if (this.isEmpty()) {
            return
        }
        val sql = """
INSERT INTO SOKNADPERIODE (ID, SYKEPENGESOKNAD_ID, FOM, TOM, GRAD, SYKMELDINGSTYPE) 
VALUES (:id, :sykepengesoknadId, :fom, :tom, :grad, :sykmeldingstype) 
ON CONFLICT ON CONSTRAINT soknadperiode_pkey DO NOTHING
"""
        jdbcTemplate.batchUpdate(
            sql,
            this
                .map {
                    MapSqlParameterSource()
                        .addValue("id", it.id)
                        .addValue("sykepengesoknadId", it.sykepengesoknadId)
                        .addValue("fom", it.fom)
                        .addValue("tom", it.tom)
                        .addValue("grad", it.grad)
                        .addValue("sykmeldingstype", it.sykmeldingstype?.name)
                }.toTypedArray(),
        )
    }

    override fun List<SporsmalDbRecord>.lagreSporsmal() {
        if (this.isEmpty()) {
            return
        }
        val sql =
            """
INSERT INTO SPORSMAL(ID, SYKEPENGESOKNAD_ID, UNDER_SPORSMAL_ID, TEKST, UNDERTEKST, TAG, SVARTYPE, MIN, MAX, KRITERIE_FOR_VISNING, METADATA) 
VALUES (:id, :sykepengesoknadId, :underSporsmalId, :tekst, :undertekst, :tag, :svartype, :min, :max, :kriterie, :metadata)
ON CONFLICT ON CONSTRAINT sporsmal_pkey DO NOTHING"""
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
                        .addValue("metadata", it.metadata)
                }.toTypedArray(),
        )
    }

    override fun List<SvarDbRecord>.lagreSvar() {
        if (this.isEmpty()) {
            return
        }
        val sql =
            """
INSERT INTO SVAR (ID, SPORSMAL_ID, VERDI) VALUES (:id, :sporsmalId, :verdi)
ON CONFLICT ON CONSTRAINT svar_pkey DO NOTHING
"""
        jdbcTemplate.batchUpdate(
            sql,
            this
                .map {
                    MapSqlParameterSource()
                        .addValue("id", it.id)
                        .addValue("sporsmalId", it.sporsmalId)
                        .addValue("verdi", it.verdi)
                }.toTypedArray(),
        )
    }
}
