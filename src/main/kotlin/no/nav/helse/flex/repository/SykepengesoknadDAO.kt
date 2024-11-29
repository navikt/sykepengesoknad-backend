package no.nav.helse.flex.repository

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.domain.exception.SlettSoknadException
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningerDokumentType
import no.nav.helse.flex.logger
import no.nav.helse.flex.medlemskap.KjentOppholdstillatelse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.medlemskap.hentKjentOppholdstillatelse
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraInntektskomponenten
import no.nav.helse.flex.soknadsopprettelse.sorterSporsmal
import no.nav.helse.flex.util.*
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
    private val soknadLagrer: SoknadLagrer,
    private val klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository,
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
) {
    val log = logger()

    class SoknadIkkeFunnetException : RuntimeException()

    fun finnSykepengesoknader(identer: FolkeregisterIdenter): List<Sykepengesoknad> = finnSykepengesoknader(identer.alle())

    fun finnSykepengesoknader(
        identer: List<String>,
        soknadstype: Soknadstype? = null,
    ): List<Sykepengesoknad> {
        val sql =
            "SELECT * FROM sykepengesoknad WHERE fnr IN (:identer)" +
                (if (soknadstype != null) " AND soknadstype = :soknadstype" else "")
        val parameters =
            MapSqlParameterSource("identer", identer).apply {
                if (soknadstype != null) {
                    addValue("soknadstype", soknadstype.name)
                }
            }

        val soknader =
            namedParameterJdbcTemplate.query(
                sql,
                parameters,
                sykepengesoknadRowMapper(),
            )

        return populerSoknadMedDataFraAndreTabeller(soknader)
    }

    fun finnSykepengesoknad(sykepengesoknadId: String): Sykepengesoknad {
        val soknader =
            namedParameterJdbcTemplate.query(
                "SELECT * FROM sykepengesoknad WHERE sykepengesoknad_uuid = :id",
                MapSqlParameterSource("id", sykepengesoknadId),
                sykepengesoknadRowMapper(),
            )

        return populerSoknadMedDataFraAndreTabeller(soknader).firstOrNull() ?: throw SoknadIkkeFunnetException()
    }

    fun finnSykepengesoknaderForSykmelding(sykmeldingId: String): List<Sykepengesoknad> {
        val soknader =
            namedParameterJdbcTemplate.query(
                "SELECT * FROM sykepengesoknad WHERE sykmelding_uuid = :sykmeldingId",
                MapSqlParameterSource("sykmeldingId", sykmeldingId),
                sykepengesoknadRowMapper(),
            )

        return populerSoknadMedDataFraAndreTabeller(soknader)
    }

    private fun populerSoknadMedDataFraAndreTabeller(soknader: MutableList<Pair<String, Sykepengesoknad>>): List<Sykepengesoknad> {
        val soknadsIder = soknader.map { it.first }.toSet()
        val soknadsUUIDer = soknader.map { it.second.id }

        val soknadsPerioder = soknadsperiodeDAO.finnSoknadPerioder(soknadsIder)
        val sporsmal = sporsmalDAO.finnSporsmal(soknadsIder)

        val klipp =
            klippetSykepengesoknadRepository
                .findAllBySykepengesoknadUuidIn(soknadsUUIDer)
                .filter { it.klippVariant.toString().startsWith("SOKNAD") }
                .groupBy { it.sykepengesoknadUuid }

        val kjenteOppholdstillatelser: Map<String, KjentOppholdstillatelse?> =
            // Vi henter kun medlemskapsvurdering for arbeidstakersøknader.
            soknader.filter { it.second.soknadstype == Soknadstype.ARBEIDSTAKERE }
                .associateBy(
                    { it.second.id },
                    {
                        medlemskapVurderingRepository.findBySykepengesoknadIdAndFomAndTom(
                            sykepengesoknadId = it.second.id,
                            fom = it.second.fom!!,
                            tom = it.second.tom!!,
                        )?.hentKjentOppholdstillatelse()
                    },
                )

        return soknader
            .map {
                it.second.copy(
                    soknadPerioder = soknadsPerioder[it.first] ?: emptyList(),
                    sporsmal = sporsmal[it.first] ?: emptyList(),
                    klippet = klipp.containsKey(it.second.id),
                    kjentOppholdstillatelse = kjenteOppholdstillatelser[it.second.id],
                )
            }
            .map { it.sorterSporsmal() }
            .sortedBy { it.opprettet }
    }

    fun finnSykepengesoknaderUtenSporsmal(identer: List<String>): List<Sykepengesoknad> {
        val soknader =
            namedParameterJdbcTemplate.query(
                " SELECT * FROM sykepengesoknad WHERE fnr IN (:identer)",
                MapSqlParameterSource()
                    .addValue("identer", identer),
                sykepengesoknadRowMapper(),
            )
        val soknadsIder = soknader.map { it.first }.toSet()
        val soknadsPerioder = soknadsperiodeDAO.finnSoknadPerioder(soknadsIder)

        return soknader
            .map {
                it.second.copy(
                    soknadPerioder = soknadsPerioder[it.first] ?: emptyList(),
                )
            }
            .sortedBy { it.opprettet }
    }

    fun finnMottakerAvSoknad(soknadUuid: String): Mottaker? {
        val mottaker =
            namedParameterJdbcTemplate.queryForObject(
                """
                SELECT CASE
                WHEN sendt_arbeidsgiver IS NOT NULL AND sendt_nav IS NOT NULL THEN 'ARBEIDSGIVER_OG_NAV'
                WHEN sendt_arbeidsgiver IS NOT NULL THEN 'ARBEIDSGIVER'
                WHEN sendt_nav IS NOT NULL THEN 'NAV'
                END
                FROM sykepengesoknad
                WHERE sykepengesoknad_uuid = :soknadUuid
                """,
                MapSqlParameterSource("soknadUuid", soknadUuid),
                Mottaker::class.java,
            )

        return mottaker
    }

    fun lagreSykepengesoknad(sykepengesoknad: Sykepengesoknad): Sykepengesoknad {
        soknadLagrer.lagreSoknad(sykepengesoknad)
        return finnSykepengesoknad(sykepengesoknad.id)
    }

    fun oppdaterKorrigertAv(sykepengesoknad: Sykepengesoknad) {
        val raderOppdatert =
            namedParameterJdbcTemplate.update(
                """
                UPDATE sykepengesoknad 
                SET korrigert_av = :korrigertAv, 
                    status = :korrigertStatus
                WHERE sykepengesoknad_uuid = :korrigerer
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("korrigertStatus", Soknadstatus.KORRIGERT.name)
                    .addValue("korrigertAv", sykepengesoknad.id)
                    .addValue("korrigerer", sykepengesoknad.korrigerer),
            )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å oppdatere korrigertAv traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun oppdaterStatus(sykepengesoknad: Sykepengesoknad) {
        val raderOppdatert =
            namedParameterJdbcTemplate.update(
                """
                UPDATE sykepengesoknad 
                SET status = :status 
                WHERE sykepengesoknad_uuid = :id
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("status", sykepengesoknad.status.name)
                    .addValue("id", sykepengesoknad.id),
            )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å oppdatere status traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun settSendtNav(
        sykepengesoknadId: String,
        sendtNav: LocalDateTime,
    ) {
        namedParameterJdbcTemplate.update(
            """
            UPDATE sykepengesoknad 
            SET sendt_nav = :sendtNav, 
                status = :status
            WHERE sykepengesoknad_uuid = :sykepengesoknadId
            AND sendt_nav IS NULL
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("status", Soknadstatus.SENDT.name)
                .addValue("sendtNav", sendtNav)
                .addValue("sykepengesoknadId", sykepengesoknadId),
        )
    }

    fun settSendtAg(
        sykepengesoknadId: String,
        sendtAg: LocalDateTime,
    ) {
        namedParameterJdbcTemplate.update(
            """
            UPDATE sykepengesoknad 
            SET sendt_arbeidsgiver = :sendtAg
            WHERE sykepengesoknad_uuid = :sykepengesoknadId
            AND sendt_arbeidsgiver IS NULL
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("sendtAg", sendtAg)
                .addValue("sykepengesoknadId", sykepengesoknadId),
        )
    }

    fun aktiverSoknad(uuid: String) {
        val raderOppdatert =
            namedParameterJdbcTemplate.update(
                """
                UPDATE sykepengesoknad 
                SET aktivert_dato = :dato, 
                    status = :status
                WHERE sykepengesoknad_uuid = :sykepengesoknadId
                AND status = 'FREMTIDIG'
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("dato", LocalDate.now())
                    .addValue("status", Soknadstatus.NY.name)
                    .addValue("sykepengesoknadId", uuid),
            )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å aktivere søknaden traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun avbrytSoknad(
        sykepengesoknad: Sykepengesoknad,
        dato: LocalDate,
    ) {
        val raderOppdatert =
            namedParameterJdbcTemplate.update(
                """
                UPDATE sykepengesoknad 
                SET avbrutt_dato = :dato, 
                    status = :status
                WHERE sykepengesoknad_uuid = :sykepengesoknadId
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("dato", dato)
                    .addValue("status", Soknadstatus.AVBRUTT.name)
                    .addValue("sykepengesoknadId", sykepengesoknad.id),
            )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å avbryte søknaden traff ikke nøyaktig en søknad som forventet!")
        }
        slettAlleSvar(sykepengesoknad)
    }

    fun gjenapneSoknad(sykepengesoknad: Sykepengesoknad) {
        val raderOppdatert =
            namedParameterJdbcTemplate.update(
                """
                UPDATE sykepengesoknad 
                SET avbrutt_dato = :dato, 
                    status = :status
                WHERE sykepengesoknad_uuid = :sykepengesoknadId
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("dato", null)
                    .addValue("status", Soknadstatus.NY.name)
                    .addValue("sykepengesoknadId", sykepengesoknad.id),
            )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å gjenåpne søknaden traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun slettAlleSvar(sykepengesoknad: Sykepengesoknad) {
        svarDAO.slettSvar(sykepengesoknad.id)
    }

    fun nullstillSoknader(fnr: String): Int {
        val soknadsIder =
            namedParameterJdbcTemplate.query(
                "SELECT id FROM sykepengesoknad WHERE (fnr = :fnr)",
                MapSqlParameterSource().addValue("fnr", fnr),
            ) { row, _ -> row.getString("id") }

        sporsmalDAO.slettSporsmal(soknadsIder)
        medlemskapVurderingRepository.deleteByFnr(fnr)

        soknadsIder.forEach {
            soknadsperiodeDAO.slettSoknadPerioder(it)
        }

        val antallSoknaderSlettet =
            if (soknadsIder.isEmpty()) {
                0
            } else {
                namedParameterJdbcTemplate.update(
                    "DELETE FROM sykepengesoknad WHERE id IN (:soknadsIder)",
                    MapSqlParameterSource()
                        .addValue("soknadsIder", soknadsIder),
                )
            }

        log.info("Slettet $antallSoknaderSlettet søknader tilhørende fnr: $fnr")

        return antallSoknaderSlettet
    }

    fun slettSoknad(sykepengesoknad: Sykepengesoknad) {
        log.info(
            "Sletter ${sykepengesoknad.status.name} søknad av typen: ${sykepengesoknad.soknadstype} med " +
                "id: ${sykepengesoknad.id} tilhørende sykmelding: ${sykepengesoknad.sykmeldingId}",
        )

        slettSoknad(sykepengesoknad.id)
    }

    fun slettSoknad(sykepengesoknadUuid: String) {
        try {
            val id = sykepengesoknadId(sykepengesoknadUuid)

            sporsmalDAO.slettSporsmal(listOf(id))
            soknadsperiodeDAO.slettSoknadPerioder(id)
            medlemskapVurderingRepository.deleteBySykepengesoknadId(sykepengesoknadUuid)

            namedParameterJdbcTemplate.update(
                "DELETE FROM sykepengesoknad WHERE id =:soknadsId",
                MapSqlParameterSource()
                    .addValue("soknadsId", id),
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

    fun byttUtSporsmal(oppdatertSoknad: Sykepengesoknad) {
        val sykepengesoknadId = sykepengesoknadId(oppdatertSoknad.id)
        sporsmalDAO.slettSporsmal(listOf(sykepengesoknadId))
        soknadLagrer.lagreSporsmalOgSvarFraSoknad(oppdatertSoknad)
    }

    fun sykepengesoknadId(uuid: String): String {
        return namedParameterJdbcTemplate.queryForObject(
            "SELECT id FROM sykepengesoknad WHERE sykepengesoknad_uuid =:uuid",
            MapSqlParameterSource()
                .addValue("uuid", uuid),
            String::class.java,
        )!!
    }

    fun klippSoknadTom(
        sykepengesoknadUuid: String,
        nyTom: LocalDate,
        tom: LocalDate,
        fom: LocalDate,
    ): List<Soknadsperiode> {
        val sykepengesoknadId = sykepengesoknadId(sykepengesoknadUuid)

        val soknadPerioder = soknadsperiodeDAO.finnSoknadPerioder(setOf(sykepengesoknadId))[sykepengesoknadId]!!
        val nyePerioder =
            soknadPerioder
                .filter { it.fom.isBeforeOrEqual(nyTom) } // Perioder som overlapper fullstendig tas ikke med
                .map {
                    if (it.tom.isAfter(nyTom)) {
                        return@map it.copy(tom = nyTom)
                    }
                    return@map it
                }

        if (nyePerioder.isEmpty()) {
            throw RuntimeException("Kan ikke klippe søknad $sykepengesoknadUuid med fullstendig overlappende perioder")
        }

        soknadsperiodeDAO.slettSoknadPerioder(
            sykepengesoknadId = sykepengesoknadId,
        )
        soknadsperiodeDAO.lagreSoknadperioder(
            sykepengesoknadId = sykepengesoknadId,
            soknadPerioder = nyePerioder,
        )
        oppdaterTom(
            sykepengesoknadId = sykepengesoknadId,
            nyTom = nyTom,
            tom = tom,
            fom = fom,
        )

        return nyePerioder
    }

    fun klippSoknadFom(
        sykepengesoknadUuid: String,
        nyFom: LocalDate,
        fom: LocalDate,
        tom: LocalDate,
    ): List<Soknadsperiode> {
        val sykepengesoknadId = sykepengesoknadId(sykepengesoknadUuid)

        val soknadPerioder = soknadsperiodeDAO.finnSoknadPerioder(setOf(sykepengesoknadId))[sykepengesoknadId]!!
        val nyePerioder =
            soknadPerioder
                .filter { it.tom.isAfterOrEqual(nyFom) } // Perioder som overlapper fullstendig tas ikke med
                .map {
                    if (it.fom.isBefore(nyFom)) {
                        return@map it.copy(fom = nyFom)
                    }
                    return@map it
                }

        if (nyePerioder.isEmpty()) {
            throw RuntimeException("Kan ikke klippe søknad $sykepengesoknadUuid med fullstendig overlappende perioder")
        }

        soknadsperiodeDAO.slettSoknadPerioder(
            sykepengesoknadId = sykepengesoknadId,
        )
        soknadsperiodeDAO.lagreSoknadperioder(
            sykepengesoknadId = sykepengesoknadId,
            soknadPerioder = nyePerioder,
        )
        oppdaterFom(
            sykepengesoknadId = sykepengesoknadId,
            nyFom = nyFom,
            fom = fom,
            tom = tom,
        )

        return nyePerioder
    }

    private fun oppdaterTom(
        sykepengesoknadId: String,
        nyTom: LocalDate,
        tom: LocalDate,
        fom: LocalDate,
    ) {
        val raderOppdatert =
            namedParameterJdbcTemplate.update(
                """
                UPDATE sykepengesoknad 
                SET tom = :nyTom 
                WHERE id = :sykepengesoknadId 
                AND fom = :fom
                AND tom = :tom 
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("nyTom", nyTom)
                    .addValue("sykepengesoknadId", sykepengesoknadId)
                    .addValue("fom", fom)
                    .addValue("tom", tom),
            )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å oppdatere tom traff ikke nøyaktig en søknad som forventet!")
        }
    }

    private fun oppdaterFom(
        sykepengesoknadId: String,
        nyFom: LocalDate,
        fom: LocalDate,
        tom: LocalDate,
    ) {
        val raderOppdatert =
            namedParameterJdbcTemplate.update(
                """
                UPDATE sykepengesoknad 
                SET fom = :nyFom
                WHERE id = :sykepengesoknadId 
                AND fom = :fom 
                AND tom = :tom
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("nyFom", nyFom)
                    .addValue("sykepengesoknadId", sykepengesoknadId)
                    .addValue("fom", fom)
                    .addValue("tom", tom),
            )

        if (raderOppdatert != 1) {
            throw RuntimeException("Spørringen for å oppdatere fom traff ikke nøyaktig en søknad som forventet!")
        }
    }

    fun sendSoknad(
        sykepengesoknad: Sykepengesoknad,
        mottaker: Mottaker,
        avsendertype: Avsendertype,
    ): Sykepengesoknad {
        val sendt = Instant.now()
        val sendtNav = if (Mottaker.NAV == mottaker || Mottaker.ARBEIDSGIVER_OG_NAV == mottaker) sendt else null
        val sendtArbeidsgiver =
            if (Mottaker.ARBEIDSGIVER == mottaker || Mottaker.ARBEIDSGIVER_OG_NAV == mottaker) sendt else null
        namedParameterJdbcTemplate.update(
            """
            UPDATE sykepengesoknad
            SET status = :statusSendt,
                avsendertype = :avsendertype,
                sendt_nav = :sendtNav,
                sendt_arbeidsgiver = :sendtArbeidsgiver,
                sendt = :sendt
            WHERE sykepengesoknad_uuid = :sykepengesoknadId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("statusSendt", Soknadstatus.SENDT.name)
                .addValue("avsendertype", avsendertype.name)
                .addValue("sendtNav", sendtNav?.tilOsloZone())
                .addValue("sendtArbeidsgiver", sendtArbeidsgiver?.tilOsloZone())
                .addValue("sendt", sendt.tilOsloZone())
                .addValue("sykepengesoknadId", sykepengesoknad.id),
        )
        return sykepengesoknad.copy(
            status = Soknadstatus.SENDT,
            avsendertype = avsendertype,
            sendtNav = sendtNav,
            sendtArbeidsgiver = sendtArbeidsgiver,
        )
    }

    private fun sykepengesoknadRowMapper(): RowMapper<Pair<String, Sykepengesoknad>> {
        return RowMapper { resultSet, _ ->
            Pair(
                resultSet.getString("id"),
                Sykepengesoknad(
                    id = resultSet.getString("sykepengesoknad_uuid"),
                    soknadstype = Soknadstype.valueOf(resultSet.getString("soknadstype")),
                    fnr = resultSet.getString("fnr"),
                    sykmeldingId = resultSet.getString("sykmelding_uuid"),
                    status = Soknadstatus.valueOf(resultSet.getString("status")),
                    fom = resultSet.getObject("fom", LocalDate::class.java),
                    tom = resultSet.getObject("tom", LocalDate::class.java),
                    opprettet = resultSet.getObject("opprettet", OffsetDateTime::class.java)?.toInstant(),
                    avbruttDato = resultSet.getObject("avbrutt_dato", LocalDate::class.java),
                    startSykeforlop = resultSet.getObject("start_sykeforlop", LocalDate::class.java),
                    sykmeldingSkrevet =
                        resultSet.getObject("sykmelding_skrevet", OffsetDateTime::class.java)
                            ?.toInstant(),
                    sykmeldingSignaturDato =
                        resultSet.getObject("sykmelding_signatur_dato", OffsetDateTime::class.java)
                            ?.toInstant(),
                    sendtNav = resultSet.getObject("sendt_nav", OffsetDateTime::class.java)?.toInstant(),
                    arbeidssituasjon =
                        Optional.ofNullable(resultSet.getString("arbeidssituasjon"))
                            .map { Arbeidssituasjon.valueOf(it) }.orElse(null),
                    sendtArbeidsgiver =
                        resultSet.getObject("sendt_arbeidsgiver", OffsetDateTime::class.java)
                            ?.toInstant(),
                    sendt = resultSet.getObject("sendt", OffsetDateTime::class.java)?.toInstant(),
                    arbeidsgiverOrgnummer = resultSet.getString("arbeidsgiver_orgnummer"),
                    arbeidsgiverNavn = resultSet.getString("arbeidsgiver_navn"),
                    korrigerer = resultSet.getString("korrigerer"),
                    korrigertAv = resultSet.getString("korrigert_av"),
                    soknadPerioder = emptyList(),
                    sporsmal = emptyList(),
                    opprinnelse = Opprinnelse.valueOf(resultSet.getString("opprinnelse")),
                    avsendertype =
                        Optional.ofNullable(resultSet.getString("avsendertype"))
                            .map { Avsendertype.valueOf(it) }.orElse(null),
                    egenmeldtSykmelding = resultSet.getNullableBoolean("egenmeldt_sykmelding"),
                    merknaderFraSykmelding = resultSet.getNullableString("merknader_fra_sykmelding").tilMerknader(),
                    opprettetAvInntektsmelding = resultSet.getBoolean("opprettet_av_inntektsmelding"),
                    utenlandskSykmelding = resultSet.getBoolean("utenlandsk_sykmelding"),
                    egenmeldingsdagerFraSykmelding = resultSet.getString("egenmeldingsdager_fra_sykmelding"),
                    inntektskilderDataFraInntektskomponenten =
                        resultSet.getNullableString("inntektskilder_data_fra_inntektskomponenten")
                            ?.tilArbeidsforholdFraInntektskomponenten(),
                    forstegangssoknad = resultSet.getNullableBoolean("forstegangssoknad"),
                    tidligereArbeidsgiverOrgnummer = resultSet.getNullableString("tidligere_arbeidsgiver_orgnummer"),
                    aktivertDato = resultSet.getObject("aktivert_dato", LocalDate::class.java),
                    inntektsopplysningerNyKvittering = resultSet.getNullableBoolean("inntektsopplysninger_ny_kvittering"),
                    inntektsopplysningerInnsendingId = resultSet.getNullableString("inntektsopplysninger_innsending_id"),
                    inntektsopplysningerInnsendingDokumenter =
                        resultSet.getNullableString("inntektsopplysninger_innsending_dokumenter")
                            ?.split(",")
                            ?.map { InntektsopplysningerDokumentType.valueOf(it) },
                    fiskerBlad =
                        Optional.ofNullable(resultSet.getString("fisker_blad"))
                            .map { FiskerBlad.valueOf(it) }.orElse(null),
                    arbeidsforholdFraAareg =
                        resultSet.getNullableString("arbeidsforhold_fra_aareg")?.let {
                            objectMapper.readValue(it)
                        },
                    julesoknad = resultSet.getNullableBoolean("aktivert_julesoknad_kandidat") ?: false,
                ),
            )
        }
    }

    data class GammeltUtkast(val sykepengesoknadUuid: String)

    fun finnGamleUtkastForSletting(): List<GammeltUtkast> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT sykepengesoknad_uuid 
            FROM sykepengesoknad 
            WHERE status = 'UTKAST_TIL_KORRIGERING' 
            AND opprettet <= :enUkeSiden
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("enUkeSiden", LocalDate.now(osloZone).atStartOfDay().minusDays(7)),
        ) { resultSet, _ ->
            GammeltUtkast(
                sykepengesoknadUuid = resultSet.getString("SYKEPENGESOKNAD_UUID"),
            )
        }
    }

    fun deaktiverSoknader(): Int {
        return namedParameterJdbcTemplate.update(
            """
            UPDATE sykepengesoknad 
            SET status = 'UTGATT'
            WHERE status IN ('NY', 'AVBRUTT')
            AND opprettet < :dato
            AND ((tom < :dato) OR (soknadstype = 'OPPHOLD_UTLAND'))
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("dato", LocalDate.now(osloZone).minusMonths(4)),
        )
    }

    fun finnUpubliserteUtlopteSoknader(): List<String> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT sykepengesoknad_uuid 
            FROM sykepengesoknad 
            WHERE utlopt_publisert IS NULL 
            AND fnr IS NOT NULL
            AND status = 'UTGATT'
            AND opprinnelse != 'SYFOSERVICE'
            LIMIT 1000
            """.trimIndent(),
            MapSqlParameterSource(),
        ) { resultSet, _ -> resultSet.getString("SYKEPENGESOKNAD_UUID") }
    }

    fun settUtloptPublisert(
        sykepengesoknadId: String,
        publisert: LocalDateTime,
    ) {
        namedParameterJdbcTemplate.update(
            """
            UPDATE sykepengesoknad 
            SET utlopt_publisert = :publisert
            WHERE sykepengesoknad_uuid = :sykepengesoknadId 
            AND utlopt_publisert IS NULL
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("publisert", publisert)
                .addValue("sykepengesoknadId", sykepengesoknadId),
        )
    }
}

fun String?.tilMerknader(): List<Merknad>? {
    this?.let {
        return objectMapper.readValue(this)
    }
    return null
}

private fun String?.tilArbeidsforholdFraInntektskomponenten(): List<ArbeidsforholdFraInntektskomponenten>? {
    this?.let {
        return objectMapper.readValue(this)
    }
    return null
}
