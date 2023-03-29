package no.nav.helse.flex.repository

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.domain.Avsendertype
import no.nav.helse.flex.domain.Merknad
import no.nav.helse.flex.domain.Mottaker
import no.nav.helse.flex.domain.Opprinnelse
import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.exception.SlettSoknadException
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.sorterSporsmal
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.isAfterOrEqual
import no.nav.helse.flex.util.isBeforeOrEqual
import no.nav.helse.flex.util.osloZone
import no.nav.helse.flex.util.tilOsloZone
import org.springframework.dao.EmptyResultDataAccessException
import org.springframework.dao.IncorrectResultSizeDataAccessException
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.*

@Transactional
@Repository
class SykepengesoknadDAO(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val soknadsperiodeDAO: SoknadsperiodeDAO,
    private val sporsmalDAO: SporsmalDAO,
    private val svarDAO: SvarDAO,
    private val soknadLagrer: SoknadLagrer
) {

    val log = logger()

    class SoknadIkkeFunnetException : RuntimeException()

    fun finnSykepengesoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> =
        finnSykepengesoknader(identer.alle())

    fun finnSykepengesoknader(identer: List<String>): List<Sykepengesoknad> {
        val soknader = namedParameterJdbcTemplate.query(
            "SELECT * FROM SYKEPENGESOKNAD " +
                "WHERE FNR IN (:identer) ",

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
            .sortedBy { it.opprettet }
    }

    fun finnSykepengesoknaderUtenSporsmal(identer: List<String>): List<Sykepengesoknad> {
        val soknader = namedParameterJdbcTemplate.query(
            "SELECT * FROM SYKEPENGESOKNAD " +
                "WHERE FNR IN (:identer) ",

            MapSqlParameterSource()
                .addValue("identer", identer),
            sykepengesoknadRowMapper()
        )
        val soknadsIder = soknader.map { it.first }.toSet()

        val soknadsPerioder = soknadsperiodeDAO.finnSoknadPerioder(soknadsIder)

        return soknader
            .map {
                it.second.copy(
                    soknadPerioder = soknadsPerioder[it.first] ?: emptyList()
                )
            }
            .sortedBy { it.opprettet }
    }

    fun finnSykepengesoknaderByUuid(soknadUuidListe: List<String>): List<Sykepengesoknad> {
        if (soknadUuidListe.isEmpty()) {
            return Collections.emptyList()
        } else {
            val soknader = namedParameterJdbcTemplate.query(
                "SELECT * FROM SYKEPENGESOKNAD " +
                    "WHERE SYKEPENGESOKNAD_UUID IN (:soknadUuidListe) ",

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

    fun finnMottakerAvSoknad(soknadUuid: String): Mottaker? {
        val mottaker = namedParameterJdbcTemplate.queryForObject(
            "SELECT CASE " +
                "WHEN SENDT_ARBEIDSGIVER IS NOT NULL AND SENDT_NAV IS NOT NULL THEN 'ARBEIDSGIVER_OG_NAV' " +
                "WHEN SENDT_ARBEIDSGIVER IS NOT NULL THEN 'ARBEIDSGIVER' " +
                "WHEN SENDT_NAV IS NOT NULL THEN 'NAV' " +
                "END " +
                "FROM SYKEPENGESOKNAD " +
                "WHERE SYKEPENGESOKNAD_UUID = :soknadUuid",
            MapSqlParameterSource("soknadUuid", soknadUuid),
            Mottaker::class.java
        )

        return mottaker
    }

    fun lagreSykepengesoknad(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
        soknadLagrer.lagreSoknad(sykepengesoknad)
        return finnSykepengesoknad(sykepengesoknad.id)
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
            "SELECT ID FROM SYKEPENGESOKNAD WHERE (fnr = :fnr)",

            MapSqlParameterSource()
                .addValue("fnr", fnr)

        ) { row, _ -> row.getString("ID") }

        sporsmalDAO.slettSporsmal(soknadsIder)
        soknadsIder.forEach { soknadsperiodeDAO.slettSoknadPerioder(it) }

        val antallSoknaderSlettet = if (soknadsIder.isEmpty()) {
            0
        } else {
            namedParameterJdbcTemplate.update(
                "DELETE FROM SYKEPENGESOKNAD WHERE ID in (:soknadsIder)",

                MapSqlParameterSource()
                    .addValue("soknadsIder", soknadsIder)
            )
        }

        log.info("Slettet $antallSoknaderSlettet soknader på fnr: $fnr")

        return antallSoknaderSlettet
    }

    fun slettSoknad(sykepengesoknad: Sykepengesoknad) {
        log.info("Sletter ${sykepengesoknad.status.name} søknad av typen: ${sykepengesoknad.soknadstype} med id: ${sykepengesoknad.id} tilhørende sykmelding: ${sykepengesoknad.sykmeldingId}")

        slettSoknad(sykepengesoknad.id)
    }

    fun slettSoknad(sykepengesoknadUuid: String) {
        try {
            val id = sykepengesoknadId(sykepengesoknadUuid)

            sporsmalDAO.slettSporsmal(listOf(id))
            soknadsperiodeDAO.slettSoknadPerioder(id)

            namedParameterJdbcTemplate.update(
                "DELETE FROM SYKEPENGESOKNAD WHERE ID =:soknadsId",

                MapSqlParameterSource()
                    .addValue("soknadsId", id)
            )
        } catch (e: EmptyResultDataAccessException) {
            log.error("Fant ingen soknad med id: {}", sykepengesoknadUuid, e)
        } catch (e: IncorrectResultSizeDataAccessException) {
            log.error("Fant flere soknader med id: {}", sykepengesoknadUuid, e)
        } catch (e: RuntimeException) {
            log.error("Feil ved sletting av søknad: {}", sykepengesoknadUuid, e)
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
        val sykepengesoknadId = sykepengesoknadId(oppdatertSoknad.id)

        sporsmalDAO.slettSporsmal(listOf(sykepengesoknadId))

        soknadLagrer.lagreSporsmalOgSvarFraSoknad(oppdatertSoknad)
    }

    fun sykepengesoknadId(uuid: String): String {
        return namedParameterJdbcTemplate.queryForObject(
            "SELECT ID FROM SYKEPENGESOKNAD WHERE SYKEPENGESOKNAD_UUID =:uuid",

            MapSqlParameterSource()
                .addValue("uuid", uuid),
            String::class.java
        )!!
    }

    fun klippSoknadTom(sykepengesoknadUuid: String, klipp: LocalDate): List<Soknadsperiode> {
        val sykepengesoknadId = sykepengesoknadId(sykepengesoknadUuid)

        val soknadPerioder = soknadsperiodeDAO.finnSoknadPerioder(setOf(sykepengesoknadId))[sykepengesoknadId]!!
        val nyePerioder = soknadPerioder
            .filter { it.fom.isBefore(klipp) } // Perioder som overlapper fullstendig tas ikke med
            .map {
                if (it.tom.isAfterOrEqual(klipp)) {
                    return@map it.copy(tom = klipp.minusDays(1))
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
            nyTom = klipp.minusDays(1)
        )

        return nyePerioder
    }

    fun klippSoknadFom(sykepengesoknadUuid: String, klipp: LocalDate): List<Soknadsperiode> {
        val sykepengesoknadId = sykepengesoknadId(sykepengesoknadUuid)

        val soknadPerioder = soknadsperiodeDAO.finnSoknadPerioder(setOf(sykepengesoknadId))[sykepengesoknadId]!!
        val nyePerioder = soknadPerioder
            .filter { it.tom.isAfter(klipp) } // Perioder som overlapper fullstendig tas ikke med
            .map {
                if (it.fom.isBeforeOrEqual(klipp)) {
                    return@map it.copy(fom = klipp.plusDays(1))
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
        oppdaterFom(
            sykepengesoknadId = sykepengesoknadId,
            nyFom = klipp.plusDays(1)
        )

        return nyePerioder
    }

    private fun oppdaterTom(sykepengesoknadId: String, nyTom: LocalDate) {
        val raderOppdatert = namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET TOM = :tom WHERE ID = :sykepengesoknadId",
            MapSqlParameterSource()
                .addValue("tom", nyTom)
                .addValue("sykepengesoknadId", sykepengesoknadId)
        )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å oppdatere tom traff ikke nøyaktig en søknad som forventet!")
        }
    }

    private fun oppdaterFom(sykepengesoknadId: String, nyFom: LocalDate) {
        val raderOppdatert = namedParameterJdbcTemplate.update(
            "UPDATE SYKEPENGESOKNAD SET FOM = :fom WHERE ID = :sykepengesoknadId",
            MapSqlParameterSource()
                .addValue("fom", nyFom)
                .addValue("sykepengesoknadId", sykepengesoknadId)
        )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å oppdatere fom traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun sendSoknad(sykepengesoknad: Sykepengesoknad, mottaker: Mottaker, avsendertype: Avsendertype): Sykepengesoknad {
        val sendt = Instant.now()
        val sendtNav = if (Mottaker.NAV == mottaker || Mottaker.ARBEIDSGIVER_OG_NAV == mottaker) sendt else null
        val sendtArbeidsgiver =
            if (Mottaker.ARBEIDSGIVER == mottaker || Mottaker.ARBEIDSGIVER_OG_NAV == mottaker) sendt else null
        namedParameterJdbcTemplate.update(
            "UPDATE sykepengesoknad " +
                "SET status = :statusSendt, " +
                "avsendertype = :avsendertype, " +
                "sendt_nav = :sendtNav, " +
                "sendt_arbeidsgiver = :sendtArbeidsgiver, " +
                "sendt = :sendt " +
                "WHERE SYKEPENGESOKNAD_UUID = :sykepengesoknadId",

            MapSqlParameterSource()
                .addValue("statusSendt", Soknadstatus.SENDT.name)
                .addValue("avsendertype", avsendertype.name)
                .addValue("sendtNav", sendtNav?.tilOsloZone())
                .addValue("sendtArbeidsgiver", sendtArbeidsgiver?.tilOsloZone())
                .addValue("sendt", sendt.tilOsloZone())
                .addValue("sykepengesoknadId", sykepengesoknad.id)
        )
        return sykepengesoknad.copy(
            status = Soknadstatus.SENDT,
            avsendertype = avsendertype,
            sendtNav = sendtNav,
            sendtArbeidsgiver = sendtArbeidsgiver
        )
    }

    private fun sykepengesoknadRowMapper(): RowMapper<Pair<String, Sykepengesoknad>> {
        return RowMapper { resultSet, _ ->
            Pair(
                resultSet.getString("ID"),
                Sykepengesoknad(
                    id = resultSet.getString("SYKEPENGESOKNAD_UUID"),
                    soknadstype = Soknadstype.valueOf(resultSet.getString("SOKNADSTYPE")),
                    fnr = resultSet.getString("FNR"),
                    sykmeldingId = resultSet.getString("SYKMELDING_UUID"),
                    status = Soknadstatus.valueOf(resultSet.getString("STATUS")),
                    fom = resultSet.getObject("FOM", LocalDate::class.java),
                    tom = resultSet.getObject("TOM", LocalDate::class.java),
                    opprettet = resultSet.getObject("OPPRETTET", OffsetDateTime::class.java)?.toInstant(),
                    avbruttDato = resultSet.getObject("AVBRUTT_DATO", LocalDate::class.java),
                    startSykeforlop = resultSet.getObject("START_SYKEFORLOP", LocalDate::class.java),
                    sykmeldingSkrevet = resultSet.getObject("SYKMELDING_SKREVET", OffsetDateTime::class.java)
                        ?.toInstant(),
                    sendtNav = resultSet.getObject("SENDT_NAV", OffsetDateTime::class.java)?.toInstant(),
                    arbeidssituasjon = Optional.ofNullable(resultSet.getString("ARBEIDSSITUASJON"))
                        .map { Arbeidssituasjon.valueOf(it) }.orElse(null),
                    sendtArbeidsgiver = resultSet.getObject("SENDT_ARBEIDSGIVER", OffsetDateTime::class.java)
                        ?.toInstant(),
                    sendt = resultSet.getObject("SENDT", OffsetDateTime::class.java)?.toInstant(),
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
                    avbruttFeilinfo = resultSet.getNullableBoolean("AVBRUTT_FEILINFO"),
                    merknaderFraSykmelding = resultSet.getNullableString("MERKNADER_FRA_SYKMELDING").tilMerknader(),
                    opprettetAvInntektsmelding = resultSet.getBoolean("OPPRETTET_AV_INNTEKTSMELDING"),
                    utenlandskSykmelding = resultSet.getBoolean("UTENLANDSK_SYKMELDING")
                )
            )
        }
    }

    data class GammeltUtkast(val sykepengesoknadUuid: String)

    fun finnGamleUtkastForSletting(): List<GammeltUtkast> {
        return namedParameterJdbcTemplate.query(
            """
                    SELECT SYKEPENGESOKNAD_UUID FROM SYKEPENGESOKNAD WHERE STATUS = 'UTKAST_TIL_KORRIGERING' AND OPPRETTET <= :enUkeSiden
                    """,
            MapSqlParameterSource()
                .addValue("enUkeSiden", LocalDate.now(osloZone).atStartOfDay().minusDays(7))
        ) { resultSet, _ ->
            GammeltUtkast(
                sykepengesoknadUuid = resultSet.getString("SYKEPENGESOKNAD_UUID")
            )
        }
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
                .addValue("dato", LocalDate.now(osloZone).minusMonths(4))
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
