package no.nav.syfo.repository

import no.nav.syfo.config.EnvironmentToggles
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDate.now

@Service
@Transactional
@Repository
class DodsmeldingDAO(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val toggle: EnvironmentToggles,
) {

    data class Dodsfall(val aktorId: String, val dodsdato: LocalDate)

    fun aktorIderMedToUkerGammelDodsmelding(): List<Dodsfall> {
        val mottattFør = if (toggle.isProduction()) {
            now().minusWeeks(2)
        } else {
            now().plusDays(1)
        }

        return namedParameterJdbcTemplate.query(
            """
                    SELECT AKTOR_ID, DODSDATO FROM DODSMELDING
                    WHERE MELDING_MOTTATT_DATO < :mottattFor
                    """,
            MapSqlParameterSource()
                .addValue("mottattFor", mottattFør)
        ) { resultSet, _ ->
            Dodsfall(
                aktorId = resultSet.getString("AKTOR_ID"),
                dodsdato = resultSet.getDate("DODSDATO").toLocalDate()
            )
        }
    }

    fun harDodsmelding(aktorId: String): Boolean {
        return namedParameterJdbcTemplate.queryForObject(
            """
                SELECT COUNT(1) FROM DODSMELDING 
                WHERE AKTOR_ID = :aktorId
            """,

            MapSqlParameterSource()
                .addValue("aktorId", aktorId),

            Integer::class.java
        )?.toInt() == 1
    }

    fun oppdaterDodsdato(aktorId: String, dodsdato: LocalDate) {
        namedParameterJdbcTemplate.update(
            """UPDATE DODSMELDING 
            SET DODSDATO = :dodsdato
            WHERE AKTOR_ID = :aktorId""",
            MapSqlParameterSource()
                .addValue("aktorId", aktorId)
                .addValue("dodsdato", dodsdato)
        )
    }

    fun lagreDodsmelding(aktorId: String, dodsdato: LocalDate, meldingMottattDato: LocalDate = now()) {
        namedParameterJdbcTemplate.update(
            """INSERT INTO DODSMELDING (
            DODSMELDING_ID, AKTOR_ID, DODSDATO, MELDING_MOTTATT_DATO)
            VALUES (
            DODSMELDING_ID_SEQ.NEXTVAL, :aktorId, :dodsdato, :meldingMottattDato)""",
            MapSqlParameterSource()
                .addValue("aktorId", aktorId)
                .addValue("dodsdato", dodsdato)
                .addValue("meldingMottattDato", meldingMottattDato)
        )
    }

    fun slettDodsmelding(aktorId: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM DODSMELDING WHERE AKTOR_ID = :aktorId",
            MapSqlParameterSource()
                .addValue("aktorId", aktorId)
        )
    }
}
