package no.nav.syfo.repository

import no.nav.syfo.config.EnvironmentToggles
import no.nav.syfo.service.FolkeregisterIdenter
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.OffsetDateTime

@Service
@Transactional
@Repository
class DodsmeldingDAO(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val toggle: EnvironmentToggles,
) {

    data class Dodsfall(val fnr: String, val dodsdato: LocalDate)

    fun fnrMedToUkerGammelDodsmelding(): List<Dodsfall> {
        val mottattFør = if (toggle.isProduction()) {
            now().minusWeeks(2)
        } else {
            now().plusDays(1)
        }

        return namedParameterJdbcTemplate.query(
            """
                    SELECT FNR, DODSDATO FROM DODSMELDING
                    WHERE MELDING_MOTTATT_DATO < :mottattFor
                    """,
            MapSqlParameterSource()
                .addValue("mottattFor", mottattFør)
        ) { resultSet, _ ->
            Dodsfall(
                fnr = resultSet.getString("FNR"),
                dodsdato = resultSet.getDate("DODSDATO").toLocalDate()
            )
        }
    }

    fun harDodsmelding(identer: FolkeregisterIdenter): Boolean {
        return namedParameterJdbcTemplate.queryForObject(
            """
                SELECT COUNT(1) FROM DODSMELDING 
                WHERE FNR IN (:identer)
            """,

            MapSqlParameterSource()
                .addValue("identer", identer.alle()),

            Integer::class.java
        )?.toInt() == 1
    }

    fun oppdaterDodsdato(identer: FolkeregisterIdenter, dodsdato: LocalDate) {
        namedParameterJdbcTemplate.update(
            """UPDATE DODSMELDING 
            SET DODSDATO = :dodsdato
            WHERE FNR IN (:identer) """,
            MapSqlParameterSource()
                .addValue("identer", identer.alle())
                .addValue("dodsdato", dodsdato)
        )
    }

    fun lagreDodsmelding(identer: FolkeregisterIdenter, dodsdato: LocalDate, meldingMottattDato: OffsetDateTime = OffsetDateTime.now()) {
        namedParameterJdbcTemplate.update(
            """INSERT INTO DODSMELDING (
            FNR, DODSDATO, MELDING_MOTTATT_DATO)
            VALUES (
             :fnr, :dodsdato, :meldingMottattDato)""",
            MapSqlParameterSource()
                .addValue("fnr", identer.originalIdent)
                .addValue("dodsdato", dodsdato)
                .addValue("meldingMottattDato", meldingMottattDato)
        )
    }

    fun slettDodsmelding(identer: FolkeregisterIdenter) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM DODSMELDING WHERE FNR IN (:identer)",
            MapSqlParameterSource()
                .addValue("identer", identer.alle())
        )
    }
}
