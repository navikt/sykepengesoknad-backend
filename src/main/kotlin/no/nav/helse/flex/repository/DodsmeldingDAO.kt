package no.nav.helse.flex.repository

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.util.osloZone
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.OffsetDateTime

interface DodsmeldingDAO {
    data class Dodsfall(
        val fnr: String,
        val dodsdato: LocalDate,
    )

    fun fnrMedToUkerGammelDodsmelding(): List<DodsmeldingDAO.Dodsfall>

    fun harDodsmelding(identer: FolkeregisterIdenter): Boolean

    fun oppdaterDodsdato(
        identer: FolkeregisterIdenter,
        dodsdato: LocalDate,
    )

    fun lagreDodsmelding(
        identer: FolkeregisterIdenter,
        dodsdato: LocalDate,
        meldingMottattDato: OffsetDateTime = OffsetDateTime.now(),
    )

    fun slettDodsmelding(identer: FolkeregisterIdenter)
}

@Transactional
@Repository
class DodsmeldingDAOPostgres(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val toggle: EnvironmentToggles,
) : DodsmeldingDAO {
    @WithSpan
    override fun fnrMedToUkerGammelDodsmelding(): List<DodsmeldingDAO.Dodsfall> {
        val mottattFør =
            if (toggle.isProduction()) {
                now(osloZone).minusWeeks(2)
            } else {
                now(osloZone).plusDays(1)
            }

        return namedParameterJdbcTemplate.query(
            """
                    SELECT FNR, DODSDATO FROM DODSMELDING
                    WHERE MELDING_MOTTATT_DATO < :mottattFor
                    """,
            MapSqlParameterSource()
                .addValue("mottattFor", mottattFør),
        ) { resultSet, _ ->
            DodsmeldingDAO.Dodsfall(
                fnr = resultSet.getString("FNR"),
                dodsdato = resultSet.getDate("DODSDATO").toLocalDate(),
            )
        }
    }

    @WithSpan
    override fun harDodsmelding(identer: FolkeregisterIdenter): Boolean =
        namedParameterJdbcTemplate
            .queryForObject(
                """
                SELECT COUNT(1) FROM DODSMELDING 
                WHERE FNR IN (:identer)
            """,
                MapSqlParameterSource()
                    .addValue("identer", identer.alle()),
                Integer::class.java,
            )?.toInt() == 1

    @WithSpan
    override fun oppdaterDodsdato(
        identer: FolkeregisterIdenter,
        dodsdato: LocalDate,
    ) {
        namedParameterJdbcTemplate.update(
            """UPDATE DODSMELDING 
            SET DODSDATO = :dodsdato
            WHERE FNR IN (:identer) """,
            MapSqlParameterSource()
                .addValue("identer", identer.alle())
                .addValue("dodsdato", dodsdato),
        )
    }

    @WithSpan
    override fun lagreDodsmelding(
        identer: FolkeregisterIdenter,
        dodsdato: LocalDate,
        meldingMottattDato: OffsetDateTime,
    ) {
        namedParameterJdbcTemplate.update(
            """INSERT INTO DODSMELDING (
            FNR, DODSDATO, MELDING_MOTTATT_DATO)
            VALUES (
             :fnr, :dodsdato, :meldingMottattDato)""",
            MapSqlParameterSource()
                .addValue("fnr", identer.originalIdent)
                .addValue("dodsdato", dodsdato)
                .addValue("meldingMottattDato", meldingMottattDato),
        )
    }

    @WithSpan
    override fun slettDodsmelding(identer: FolkeregisterIdenter) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM DODSMELDING WHERE FNR IN (:identer)",
            MapSqlParameterSource()
                .addValue("identer", identer.alle()),
        )
    }
}
