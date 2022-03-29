package no.nav.syfo.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Transactional
@Repository
class JulesoknadkandidatDAO(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate
) {

    data class Julesoknadkandidat(val julesoknadkandidatId: String, val sykepengesoknadUuid: String)

    fun hentJulesoknadkandidater(): List<Julesoknadkandidat> {

        return namedParameterJdbcTemplate.query(
            """
                    SELECT JULESOKNADKANDIDAT_ID, SYKEPENGESOKNAD_UUID FROM JULESOKNADKANDIDAT
                    """
        ) { resultSet, _ ->
            Julesoknadkandidat(
                julesoknadkandidatId = resultSet.getString("JULESOKNADKANDIDAT_ID"),
                sykepengesoknadUuid = resultSet.getString("SYKEPENGESOKNAD_UUID")
            )
        }
    }

    fun lagreJulesoknadkandidat(sykepengesoknadUuid: String) {
        namedParameterJdbcTemplate.update(
            """
                    INSERT INTO JULESOKNADKANDIDAT (JULESOKNADKANDIDAT_ID, SYKEPENGESOKNAD_UUID, OPPRETTET)
                    VALUES (JULESOKNADKANDIDAT_ID_SEQ.NEXTVAL, :sykepengesoknadUuid, :opprettet)
                    """,
            MapSqlParameterSource()
                .addValue("sykepengesoknadUuid", sykepengesoknadUuid)
                .addValue("opprettet", LocalDateTime.now())
        )
    }

    fun slettJulesoknadkandidat(julesoknadkandidatId: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM JULESOKNADKANDIDAT WHERE JULESOKNADKANDIDAT_ID = :julesoknadkandidatId",
            MapSqlParameterSource()
                .addValue("julesoknadkandidatId", julesoknadkandidatId)
        )
    }
}
