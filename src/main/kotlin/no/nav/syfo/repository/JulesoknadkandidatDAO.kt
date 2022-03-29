package no.nav.syfo.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Transactional
@Repository
class JulesoknadkandidatDAO(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate
) {

    data class Julesoknadkandidat(val julesoknadkandidatId: String, val sykepengesoknadUuid: String)

    fun hentJulesoknadkandidater(): List<Julesoknadkandidat> {

        return namedParameterJdbcTemplate.query(
            """
                    SELECT ID, SYKEPENGESOKNAD_UUID FROM JULESOKNADKANDIDAT
                    """
        ) { resultSet, _ ->
            Julesoknadkandidat(
                julesoknadkandidatId = resultSet.getString("ID"),
                sykepengesoknadUuid = resultSet.getString("SYKEPENGESOKNAD_UUID")
            )
        }
    }

    fun lagreJulesoknadkandidat(sykepengesoknadUuid: String) {
        namedParameterJdbcTemplate.update(
            """
                    INSERT INTO JULESOKNADKANDIDAT (SYKEPENGESOKNAD_UUID, OPPRETTET)
                    VALUES (:sykepengesoknadUuid, :opprettet)
                    """,
            MapSqlParameterSource()
                .addValue("sykepengesoknadUuid", sykepengesoknadUuid)
                .addValue("opprettet", OffsetDateTime.now())
        )
    }

    fun slettJulesoknadkandidat(julesoknadkandidatId: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM JULESOKNADKANDIDAT WHERE ID = :julesoknadkandidatId",
            MapSqlParameterSource()
                .addValue("julesoknadkandidatId", julesoknadkandidatId)
        )
    }
}
