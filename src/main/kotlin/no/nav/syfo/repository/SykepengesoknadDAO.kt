package no.nav.syfo.repository

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.syfo.domain.*
import no.nav.syfo.domain.exception.SlettSoknadException
import no.nav.syfo.logger
import no.nav.syfo.service.FolkeregisterIdenter
import no.nav.syfo.soknadsopprettelse.sorterSporsmal
import no.nav.syfo.util.OBJECT_MAPPER
import no.nav.syfo.util.isAfterOrEqual
import no.nav.syfo.util.serialisertTilString
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

@Transactional
@Repository
class SykepengesoknadDAO(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val soknadsperiodeDAO: SoknadsperiodeDAO,
    private val sporsmalDAO: SporsmalDAO,
    private val svarDAO: SvarDAO
) {

    val log = logger()

    class SoknadIkkeFunnetException : RuntimeException()

    fun finnSykepengesoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> =
        finnSykepengesoknader(identer.alle())

    fun finnSykepengesoknader(identer: List<String>): List<Sykepengesoknad> {
        val soknader = namedParameterJdbcTemplate.query(
            "SELECT * FROM SYKEPENGESOKNAD " +
                "WHERE FNR IN (:identer) " +
                "ORDER BY SYKEPENGESOKNAD_ID",

            MapSqlParameterSource()
                .addValue("identer", identer),
            sykepengesoknadRowMapper()
        )
        val soknadsIder = soknader.map { it.first }.toSet()

        val soknadsPerioder = soknadsperiodeDAO.finnSoknadPerioder(soknadsIder)
        val sporsmal = sporsmalDAO.finnSporsmal(soknadsIder)

        return soknader
            .map {
                it.second.copy(
                    soknadPerioder = soknadsPerioder[it.first] ?: emptyList(),
                    sporsmal = sporsmal[it.first] ?: emptyList()
                )
            }
            .map { it.sorterSporsmal() }
    }

    fun finnSykepengesoknaderForNl(fnr: String, orgnummer: String, tilgangFom: LocalDate): List<Sykepengesoknad> {
        val soknader = namedParameterJdbcTemplate.query(
            "SELECT * FROM SYKEPENGESOKNAD " +
                "WHERE FNR = :fnr " +
                "AND ARBEIDSGIVER_ORGNUMMER = :orgnummer " +
                "AND ARBEIDSGIVER_ORGNUMMER IS NOT NULL " +
                "AND TOM >= :tilgangFom " +
                "ORDER BY SYKEPENGESOKNAD_ID",

            MapSqlParameterSource()
                .addValue("fnr", fnr)
                .addValue("orgnummer", orgnummer)
                .addValue("tilgangFom", tilgangFom),

            sykepengesoknadRowMapper()
        )
        val soknadsIder = soknader.map { it.first }.toSet()

        val soknadsPerioder = soknadsperiodeDAO.finnSoknadPerioder(soknadsIder)
        val sporsmal = sporsmalDAO.finnSporsmal(soknadsIder)

        return soknader
            .map {
                it.second.copy(
                    soknadPerioder = soknadsPerioder[it.first] ?: emptyList(),
                    sporsmal = sporsmal[it.first] ?: emptyList()
                )
            }
            .map { it.sorterSporsmal() }
    }

    fun finnSykepengesoknaderByUuid(soknadUuidListe: List<String>): List<Sykepengesoknad> {
        if (soknadUuidListe.isEmpty()) {
            return Collections.emptyList()
        } else {
            val soknader = namedParameterJdbcTemplate.query(
                "SELECT * FROM SYKEPENGESOKNAD " +
                    "WHERE SYKEPENGESOKNAD_UUID IN (:soknadUuidListe) " +
                    "ORDER BY SYKEPENGESOKNAD_ID",

                MapSqlParameterSource()
                    .addValue("soknadUuidListe", soknadUuidListe),

                sykepengesoknadRowMapper()
            )
            val soknadsIder = soknader.map { it.first }.toSet()

            val soknadsPerioder = soknadsperiodeDAO.finnSoknadPerioder(soknadsIder)
            val sporsmal = sporsmalDAO.finnSporsmal(soknadsIder)

            return soknader
                .map {
                    it.second.copy(
                        soknadPerioder = soknadsPerioder[it.first] ?: emptyList(),
                        sporsmal = sporsmal[it.first] ?: emptyList()
                    )
                }
                .map { it.sorterSporsmal() }
        }
    }

    fun finnMottakerAvSoknad(soknadUuid: String): Optional<Mottaker> {
        val mottaker = namedParameterJdbcTemplate.queryForObject(
            "SELECT CASE " +
                "WHEN SENDT_ARBEIDSGIVER IS NOT NULL AND SENDT_NAV IS NOT NULL THEN 'ARBEIDSGIVER_OG_NAV' " +
                "WHEN SENDT_ARBEIDSGIVER IS NOT NULL THEN 'ARBEIDSGIVER' " +
                "WHEN SENDT_NAV IS NOT NULL THEN 'NAV' " +
                "ELSE NULL " +
                "END " +
                "FROM SYKEPENGESOKNAD " +
                "WHERE SYKEPENGESOKNAD_UUID = :soknadUuid",
            MapSqlParameterSource("soknadUuid", soknadUuid),
            String::class.java
        )
        if (mottaker != null) {
            return Optional.of(Mottaker.valueOf(mottaker))
        }
        return Optional.empty()
    }

    fun lagreSykepengesoknad(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
        val generatedKeyHolder = GeneratedKeyHolder()
        val merknader = sykepengesoknad.merknaderFraSykmelding?.serialisertTilString()
        namedParameterJdbcTemplate.update(
            """INSERT INTO SYKEPENGESOKNAD (SYKEPENGESOKNAD_ID, SYKEPENGESOKNAD_UUID, AKTOR_ID, SOKNADSTYPE, STATUS, FOM, TOM, OPPRETTET, AVBRUTT_DATO, SYKMELDING_UUID, SENDT_NAV, SENDT_ARBEIDSGIVER, KORRIGERER, KORRIGERT_AV, ARBEIDSGIVER_ORGNUMMER, ARBEIDSGIVER_NAVN, ARBEIDSSITUASJON, START_SYKEFORLOP, SYKMELDING_SKREVET, ARBEIDSGIVER_FORSKUTTERER, OPPRINNELSE, FNR, EGENMELDT_SYKMELDING, MERKNADER_FRA_SYKMELDING, AVBRUTT_FEILINFO) VALUES (SYKEPENGESOKNAD_ID_SEQ.NEXTVAL, :uuid, :aktorId, :soknadstype, :status, :fom, :tom, :opprettet, :avbrutt, :sykmeldingUuid, :sendtNav, :sendtArbeidsgiver, :korrigerer, :korrigertAv, :arbeidsgiverOrgnummer, :arbeidsgiverNavn, :arbeidssituasjon, :startSykeforlop, :sykmeldingSkrevet, :arbeidsgiverForskutterer, :opprinnelse, :fnr, :egenmeldtSykmelding, :merknaderFraSykmelding, :avbruttFeilinfo)""",

            MapSqlParameterSource()
                .addValue("uuid", sykepengesoknad.id)
                .addValue("aktorId", "ubrukt")
                .addValue("soknadstype", sykepengesoknad.soknadstype.name)
                .addValue("status", sykepengesoknad.status.name)
                .addValue("fom", sykepengesoknad.fom)
                .addValue("tom", sykepengesoknad.tom)
                .addValue("opprettet", sykepengesoknad.opprettet)
                .addValue("avbrutt", sykepengesoknad.avbruttDato)
                .addValue("sendtNav", sykepengesoknad.sendtNav)
                .addValue("sendtArbeidsgiver", sykepengesoknad.sendtArbeidsgiver)
                .addValue("sykmeldingUuid", sykepengesoknad.sykmeldingId)
                .addValue("korrigerer", sykepengesoknad.korrigerer)
                .addValue("korrigertAv", sykepengesoknad.korrigertAv)
                .addValue("arbeidsgiverOrgnummer", sykepengesoknad.arbeidsgiverOrgnummer)
                .addValue("arbeidsgiverNavn", sykepengesoknad.arbeidsgiverNavn)
                .addValue("arbeidssituasjon", sykepengesoknad.arbeidssituasjon?.name)
                .addValue("startSykeforlop", sykepengesoknad.startSykeforlop)
                .addValue("sykmeldingSkrevet", sykepengesoknad.sykmeldingSkrevet)
                .addValue(
                    "arbeidsgiverForskutterer",
                    if (sykepengesoknad.arbeidsgiverForskutterer == null)
                        null
                    else
                        sykepengesoknad.arbeidsgiverForskutterer.name
                )
                .addValue("opprinnelse", sykepengesoknad.opprinnelse.name)
                .addValue("fnr", sykepengesoknad.fnr)
                .addValue("egenmeldtSykmelding", sykepengesoknad.egenmeldtSykmelding.tilDatabaseBoolean())
                .addValue("avbruttFeilinfo", sykepengesoknad.avbruttFeilinfo.tilDatabaseBoolean(trueValue = "J"))
                .addValue("merknaderFraSykmelding", merknader),
            generatedKeyHolder,
            arrayOf("SYKEPENGESOKNAD_ID")
        )

        val sykepengesoknadId = generatedKeyHolder.key!!.toLong()
        sykepengesoknad.soknadPerioder?.let {
            soknadsperiodeDAO.lagreSoknadperioder(sykepengesoknadId, it)
        }
        val lagretSporsmal =
            sykepengesoknad.sporsmal.map { sporsmal -> sporsmalDAO.lagreSporsmal(sykepengesoknadId, sporsmal, null) }
        return sykepengesoknad.copy(sporsmal = lagretSporsmal)
    }

    fun oppdaterKorrigertAv(sykepengesoknad: Sykepengesoknad) {
        val raderOppdatert = namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET KORRIGERT_AV = :korrigertAv, STATUS = :korrigertStatus " + "WHERE SYKEPENGESOKNAD_UUID = :korrigerer",

            MapSqlParameterSource()
                .addValue("korrigertStatus", Soknadstatus.KORRIGERT.name)
                .addValue("korrigertAv", sykepengesoknad.id)
                .addValue("korrigerer", sykepengesoknad.korrigerer)
        )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å oppdatere korrigertAv traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun oppdaterStatus(sykepengesoknad: Sykepengesoknad) {
        val raderOppdatert = namedParameterJdbcTemplate.update(
            """UPDATE SYKEPENGESOKNAD SET STATUS = :status WHERE SYKEPENGESOKNAD_UUID = :id""",

            MapSqlParameterSource()
                .addValue("status", sykepengesoknad.status.name)
                .addValue("id", sykepengesoknad.id)
        )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å oppdatere status traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun settSendtNav(sykepengesoknadId: String, sendtNav: LocalDateTime) {
        namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET SENDT_NAV = :sendtNav, STATUS = :status " +
                "WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId " +
                "AND SENDT_NAV IS NULL",

            MapSqlParameterSource()
                .addValue("status", Soknadstatus.SENDT.name)
                .addValue("sendtNav", sendtNav)
                .addValue("sykepengesoknadId", sykepengesoknadId)
        )
    }

    fun settSendtAg(sykepengesoknadId: String, sendtAg: LocalDateTime) {
        namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET SENDT_ARBEIDSGIVER = :sendtAg " +
                "WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId " +
                "AND SENDT_ARBEIDSGIVER IS NULL",

            MapSqlParameterSource()
                .addValue("sendtAg", sendtAg)
                .addValue("sykepengesoknadId", sykepengesoknadId)
        )
    }

    fun aktiverSoknad(uuid: String) {
        val raderOppdatert = namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET AKTIVERT_DATO = :dato, STATUS = :status " +
                "WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId " +
                "AND STATUS = 'FREMTIDIG'",
            MapSqlParameterSource()
                .addValue("dato", LocalDate.now())
                .addValue("status", Soknadstatus.NY.name)
                .addValue("sykepengesoknadId", uuid)
        )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å aktivere søknaden traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun avbrytSoknad(sykepengesoknad: Sykepengesoknad, dato: LocalDate) {
        val raderOppdatert = namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET AVBRUTT_DATO = :dato, STATUS = :status " + "WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId",
            MapSqlParameterSource()
                .addValue("dato", dato)
                .addValue("status", Soknadstatus.AVBRUTT.name)
                .addValue("sykepengesoknadId", sykepengesoknad.id)
        )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å avbryte søknaden traff ikke nøyaktig en søknad som forventet!")
        }
        slettAlleSvar(sykepengesoknad)
    }

    fun gjenapneSoknad(sykepengesoknad: Sykepengesoknad) {
        val raderOppdatert = namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET AVBRUTT_DATO = :dato, STATUS = :status " + "WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId",
            MapSqlParameterSource()
                .addValue("dato", null)
                .addValue("status", Soknadstatus.NY.name)
                .addValue("sykepengesoknadId", sykepengesoknad.id)
        )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å gjenåpne søknaden traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun slettAlleSvar(sykepengesoknad: Sykepengesoknad) {
        svarDAO.slettSvar(sykepengesoknad.id)
    }

    fun nullstillSoknader(fnr: String): Int {
        val soknadsIder = namedParameterJdbcTemplate.query(
            "SELECT SYKEPENGESOKNAD_ID FROM SYKEPENGESOKNAD WHERE (fnr = :fnr)",

            MapSqlParameterSource()
                .addValue("fnr", fnr)

        ) { row, _ -> row.getLong("SYKEPENGESOKNAD_ID") }

        sporsmalDAO.slettSporsmal(soknadsIder)
        soknadsIder.forEach { soknadsperiodeDAO.slettSoknadPerioder(it) }

        val antallSoknaderSlettet = if (soknadsIder.isEmpty())
            0
        else
            namedParameterJdbcTemplate.update(
                "DELETE FROM SYKEPENGESOKNAD WHERE SYKEPENGESOKNAD_ID in (:soknadsIder)",

                MapSqlParameterSource()
                    .addValue("soknadsIder", soknadsIder)
            )

        log.info("Slettet $antallSoknaderSlettet soknader på fnr: $fnr")

        return antallSoknaderSlettet
    }

    fun nullstillSoknaderMedFnr(fnr: String): Int {
        val soknadsIder = namedParameterJdbcTemplate.query(
            "SELECT SYKEPENGESOKNAD_ID FROM SYKEPENGESOKNAD WHERE (FNR = :fnr)",

            MapSqlParameterSource()
                .addValue("fnr", fnr)

        ) { row, _ -> row.getLong("SYKEPENGESOKNAD_ID") }

        sporsmalDAO.slettSporsmal(soknadsIder)
        soknadsIder.forEach { soknadsperiodeDAO.slettSoknadPerioder(it) }

        val antallSoknaderSlettet = if (soknadsIder.isEmpty())
            0
        else
            namedParameterJdbcTemplate.update(
                "DELETE FROM SYKEPENGESOKNAD WHERE SYKEPENGESOKNAD_ID in (:soknadsIder)",

                MapSqlParameterSource()
                    .addValue("soknadsIder", soknadsIder)
            )

        log.info("Slettet $antallSoknaderSlettet soknader på fnr: $fnr")

        return antallSoknaderSlettet
    }

    fun slettSoknad(sykepengesoknad: Sykepengesoknad) {
        log.info("Sletter ${sykepengesoknad.status.name} søknad av typen: ${sykepengesoknad.soknadstype} med id: ${sykepengesoknad.id} tilhørende sykmelding: ${sykepengesoknad.sykmeldingId}")

        slettSoknad(sykepengesoknad.id)
    }

    fun slettSoknad(sykepengesoknadUuid: String) {

        try {
            val id = namedParameterJdbcTemplate.queryForObject(
                "SELECT SYKEPENGESOKNAD_ID FROM SYKEPENGESOKNAD WHERE SYKEPENGESOKNAD_UUID =:id",

                MapSqlParameterSource()
                    .addValue("id", sykepengesoknadUuid),
                Long::class.java
            )!!

            sporsmalDAO.slettSporsmal(listOf(id))
            soknadsperiodeDAO.slettSoknadPerioder(id)

            namedParameterJdbcTemplate.update(
                "DELETE FROM SYKEPENGESOKNAD WHERE SYKEPENGESOKNAD_ID =:soknadsId",

                MapSqlParameterSource()
                    .addValue("soknadsId", id)
            )
        } catch (e: EmptyResultDataAccessException) {
            log.error("Fant ingen soknad med id: {}", sykepengesoknadUuid)
        } catch (e: IncorrectResultSizeDataAccessException) {
            log.error("Fant flere soknader med id: {}", sykepengesoknadUuid)
        } catch (e: RuntimeException) {
            log.error("Feil ved sletting av søknad: {}", sykepengesoknadUuid)
            throw SlettSoknadException()
        }
    }

    fun finnAlleredeOpprettetSoknad(identer: FolkeregisterIdenter): Sykepengesoknad? {
        return finnSykepengesoknader(identer)
            .firstOrNull { s -> Soknadstatus.NY == s.status && Soknadstype.OPPHOLD_UTLAND == s.soknadstype }
    }

    fun finnSykepengesoknad(sykepengesoknadId: String): Sykepengesoknad {
        val soknader = namedParameterJdbcTemplate.query(
            "SELECT * FROM SYKEPENGESOKNAD WHERE SYKEPENGESOKNAD_UUID = :id",
            MapSqlParameterSource("id", sykepengesoknadId),
            sykepengesoknadRowMapper()
        )
        val soknadsIder = soknader.map { it.first }.toSet()

        val soknadsPerioder = soknadsperiodeDAO.finnSoknadPerioder(soknadsIder)
        val sporsmal = sporsmalDAO.finnSporsmal(soknadsIder)

        return soknader
            .map {
                it.second.copy(
                    soknadPerioder = soknadsPerioder[it.first] ?: emptyList(),
                    sporsmal = sporsmal[it.first] ?: emptyList()
                )
            }
            .map { it.sorterSporsmal() }
            .firstOrNull() ?: throw SoknadIkkeFunnetException()
    }

    fun finnSykepengesoknaderForSykmelding(sykmeldingId: String): List<Sykepengesoknad> {
        val soknader = namedParameterJdbcTemplate.query(
            "SELECT * FROM SYKEPENGESOKNAD WHERE SYKMELDING_UUID = :sykmeldingId",

            MapSqlParameterSource()
                .addValue("sykmeldingId", sykmeldingId),

            sykepengesoknadRowMapper()
        )
        val soknadsIder = soknader.map { it.first }.toSet()

        val soknadsPerioder = soknadsperiodeDAO.finnSoknadPerioder(soknadsIder)
        val sporsmal = sporsmalDAO.finnSporsmal(soknadsIder)

        return soknader
            .map {
                it.second.copy(
                    soknadPerioder = soknadsPerioder[it.first] ?: emptyList(),
                    sporsmal = sporsmal[it.first] ?: emptyList()
                )
            }
            .map { it.sorterSporsmal() }
    }

    fun byttUtSporsmal(oppdatertSoknad: Sykepengesoknad) {
        val sykepengesoknadId = namedParameterJdbcTemplate.queryForObject(
            "SELECT SYKEPENGESOKNAD_ID FROM SYKEPENGESOKNAD WHERE SYKEPENGESOKNAD_UUID =:id",

            MapSqlParameterSource()
                .addValue("id", oppdatertSoknad.id),
            Long::class.java
        )!!

        sporsmalDAO.slettSporsmal(listOf(sykepengesoknadId))

        oppdatertSoknad.sporsmal.forEach { sporsmal -> sporsmalDAO.lagreSporsmal(sykepengesoknadId, sporsmal, null) }
    }

    fun klippSoknad(sykepengesoknadUuid: String, klippFom: LocalDate) {
        val sykepengesoknadId = namedParameterJdbcTemplate.queryForObject(
            "SELECT SYKEPENGESOKNAD_ID FROM SYKEPENGESOKNAD WHERE SYKEPENGESOKNAD_UUID =:uuid",

            MapSqlParameterSource()
                .addValue("uuid", sykepengesoknadUuid),
            Long::class.java
        )!!

        val soknadPerioder = soknadsperiodeDAO.finnSoknadPerioder(setOf(sykepengesoknadId))[sykepengesoknadId]!!
        val nyePerioder = soknadPerioder
            .filter { it.fom.isBefore(klippFom) } // Perioder som overlapper fullstendig tas ikke med
            .map {
                if (it.tom.isAfterOrEqual(klippFom)) {
                    return@map it.copy(tom = klippFom.minusDays(1))
                }
                return@map it
            }

        if (nyePerioder.isEmpty()) {
            throw RuntimeException("Kan ikke klippe søknad $sykepengesoknadUuid med fullstendig overlappende perioder")
        }

        soknadsperiodeDAO.slettSoknadPerioder(
            sykepengesoknadId = sykepengesoknadId
        )
        soknadsperiodeDAO.lagreSoknadperioder(
            sykepengesoknadId = sykepengesoknadId,
            soknadPerioder = nyePerioder
        )
        oppdaterTom(
            sykepengesoknadId = sykepengesoknadId,
            nyTom = klippFom.minusDays(1)
        )
    }

    private fun oppdaterTom(sykepengesoknadId: Long, nyTom: LocalDate) {
        val raderOppdatert = namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET TOM = :tom WHERE SYKEPENGESOKNAD_ID = :sykepengesoknadId",
            MapSqlParameterSource()
                .addValue("tom", nyTom)
                .addValue("sykepengesoknadId", sykepengesoknadId)
        )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å oppdatere tom traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun sendSoknad(sykepengesoknad: Sykepengesoknad, mottaker: Mottaker, avsendertype: Avsendertype): Sykepengesoknad {
        val sendt = LocalDateTime.now()
        val sendtNav = if (Mottaker.NAV == mottaker || Mottaker.ARBEIDSGIVER_OG_NAV == mottaker) sendt else null
        val sendtArbeidsgiver =
            if (Mottaker.ARBEIDSGIVER == mottaker || Mottaker.ARBEIDSGIVER_OG_NAV == mottaker) sendt else null
        namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD " +
                "SET STATUS = :statusSendt, " +
                "AVSENDERTYPE = :avsendertype, " +
                "SENDT_NAV = :sendtNav, " +
                "SENDT_ARBEIDSGIVER = :sendtArbeidsgiver " +
                "WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId",

            MapSqlParameterSource()
                .addValue("statusSendt", Soknadstatus.SENDT.name)
                .addValue("avsendertype", avsendertype.name)
                .addValue("sendtNav", sendtNav)
                .addValue("sendtArbeidsgiver", sendtArbeidsgiver)
                .addValue("sykepengesoknadId", sykepengesoknad.id)
        )
        return sykepengesoknad.copy(
            status = Soknadstatus.SENDT,
            avsendertype = avsendertype,
            sendtNav = sendtNav,
            sendtArbeidsgiver = sendtArbeidsgiver
        )
    }

    private fun sykepengesoknadRowMapper(): RowMapper<Pair<Long, Sykepengesoknad>> {
        return RowMapper { resultSet, _ ->
            Pair(
                resultSet.getLong("SYKEPENGESOKNAD_ID"),
                Sykepengesoknad(
                    id = resultSet.getString("SYKEPENGESOKNAD_UUID"),
                    soknadstype = Soknadstype.valueOf(resultSet.getString("SOKNADSTYPE")),
                    fnr = resultSet.getString("FNR"),
                    sykmeldingId = resultSet.getString("SYKMELDING_UUID"),
                    status = Soknadstatus.valueOf(resultSet.getString("STATUS")),
                    fom = resultSet.getObject("FOM", LocalDate::class.java),
                    tom = resultSet.getObject("TOM", LocalDate::class.java),
                    opprettet = resultSet.getObject("OPPRETTET", LocalDateTime::class.java),
                    avbruttDato = resultSet.getObject("AVBRUTT_DATO", LocalDate::class.java),
                    startSykeforlop = resultSet.getObject("START_SYKEFORLOP", LocalDate::class.java),
                    sykmeldingSkrevet = resultSet.getObject("SYKMELDING_SKREVET", LocalDateTime::class.java),
                    sendtNav = resultSet.getObject("SENDT_NAV", LocalDateTime::class.java),
                    arbeidssituasjon = Optional.ofNullable(resultSet.getString("ARBEIDSSITUASJON"))
                        .map { Arbeidssituasjon.valueOf(it) }.orElse(null),
                    sendtArbeidsgiver = resultSet.getObject("SENDT_ARBEIDSGIVER", LocalDateTime::class.java),
                    arbeidsgiverForskutterer = Optional.ofNullable(resultSet.getString("ARBEIDSGIVER_FORSKUTTERER"))
                        .map { ArbeidsgiverForskutterer.valueOf(it) }.orElse(null),
                    arbeidsgiverOrgnummer = resultSet.getString("ARBEIDSGIVER_ORGNUMMER"),
                    arbeidsgiverNavn = resultSet.getString("ARBEIDSGIVER_NAVN"),
                    korrigerer = resultSet.getString("KORRIGERER"),
                    korrigertAv = resultSet.getString("KORRIGERT_AV"),
                    soknadPerioder = emptyList(),
                    sporsmal = emptyList(),
                    opprinnelse = Opprinnelse.valueOf(resultSet.getString("OPPRINNELSE")),
                    avsendertype = Optional.ofNullable(resultSet.getString("AVSENDERTYPE"))
                        .map { Avsendertype.valueOf(it) }.orElse(null),
                    egenmeldtSykmelding = resultSet.getNullableBoolean("EGENMELDT_SYKMELDING"),
                    avbruttFeilinfo = resultSet.getNullableBoolean("AVBRUTT_FEILINFO", "J"),
                    merknaderFraSykmelding = resultSet.getNullableString("MERKNADER_FRA_SYKMELDING").tilMerknader()
                )
            )
        }
    }

    private fun ResultSet.getNullableString(columnLabel: String): String? {
        return this.getString(columnLabel)
    }

    private fun ResultSet.getNullableBoolean(columnLabel: String, trueValue: String = "Y"): Boolean? {
        val string = this.getString(columnLabel)
        if (string == trueValue) {
            return true
        }
        if (string == "N") {
            return false
        }
        return null
    }

    private fun Boolean?.tilDatabaseBoolean(trueValue: String = "Y"): String? {
        if (this == null) {
            return null
        }
        if (this == true) {
            return trueValue
        }
        return "N"
    }

    data class GammeltUtkast(val sykepengesoknadUuid: String)

    fun finnGamleUtkastForSletting(): List<GammeltUtkast> {

        return namedParameterJdbcTemplate.query(
            """
                    SELECT SYKEPENGESOKNAD_UUID FROM SYKEPENGESOKNAD WHERE STATUS = 'UTKAST_TIL_KORRIGERING' AND OPPRETTET <= :enUkeSiden
                    """,
            MapSqlParameterSource()
                .addValue("enUkeSiden", LocalDate.now().atStartOfDay().minusDays(7))
        ) { resultSet, _ ->
            GammeltUtkast(
                sykepengesoknadUuid = resultSet.getString("SYKEPENGESOKNAD_UUID")
            )
        }
    }

    fun finnSoknaderSomSkalAktiveres(now: LocalDate): List<String> {

        return namedParameterJdbcTemplate.query(
            "SELECT SYKEPENGESOKNAD_UUID FROM SYKEPENGESOKNAD WHERE TOM < :now AND STATUS = 'FREMTIDIG'",
            MapSqlParameterSource()
                .addValue("now", now)
        ) { resultSet, _ -> resultSet.getString("SYKEPENGESOKNAD_UUID") }
    }

    fun deaktiverSoknader(): Int {
        return namedParameterJdbcTemplate.update(
            """
                        UPDATE SYKEPENGESOKNAD SET STATUS = 'UTGATT'
                        WHERE STATUS IN ('NY', 'AVBRUTT')
                        AND OPPRETTET < :dato
                        AND AVBRUTT_FEILINFO IS NULL
                        AND ((TOM < :dato) OR (SOKNADSTYPE = 'OPPHOLD_UTLAND'))""",
            MapSqlParameterSource()
                .addValue("dato", LocalDate.now().minusMonths(4))
        )
    }

    fun finnUpubliserteUtlopteSoknader(): List<String> {
        return namedParameterJdbcTemplate.query(
            """
                SELECT SYKEPENGESOKNAD_UUID 
                FROM SYKEPENGESOKNAD 
                WHERE UTLOPT_PUBLISERT IS NULL 
                AND FNR IS NOT NULL
                AND STATUS = 'UTGATT'
                AND OPPRINNELSE != 'SYFOSERVICE'
                FETCH FIRST 1000 ROWS ONLY
            """.trimIndent(),
            MapSqlParameterSource()
        ) { resultSet, _ -> resultSet.getString("SYKEPENGESOKNAD_UUID") }
    }

    fun settUtloptPublisert(sykepengesoknadId: String, publisert: LocalDateTime) {
        namedParameterJdbcTemplate.update(
            """UPDATE SYKEPENGESOKNAD SET UTLOPT_PUBLISERT = :publisert WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId AND UTLOPT_PUBLISERT IS NULL""",

            MapSqlParameterSource()
                .addValue("publisert", publisert)
                .addValue("sykepengesoknadId", sykepengesoknadId)
        )
    }
}

private fun String?.tilMerknader(): List<Merknad>? {
    this?.let {
        return OBJECT_MAPPER.readValue(this)
    }
    return null
}
