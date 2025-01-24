package no.nav.helse.flex.repository

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

interface JulesoknadkandidatDAO {
    data class Julesoknadkandidat(val julesoknadkandidatId: String, val sykepengesoknadUuid: String)

    fun hentJulesoknadkandidater(): List<JulesoknadkandidatDAO.Julesoknadkandidat>

    fun lagreJulesoknadkandidat(sykepengesoknadUuid: String)

    fun slettJulesoknadkandidat(julesoknadkandidatId: String)
}

@Transactional
@Repository
class JulesoknadkandidatDAOImpl(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
) : JulesoknadkandidatDAO {
    override fun hentJulesoknadkandidater(): List<JulesoknadkandidatDAO.Julesoknadkandidat> {
        return namedParameterJdbcTemplate.query(
            """
            SELECT id, sykepengesoknad_uuid 
            FROM julesoknadkandidat
            """,
        ) { resultSet, _ ->
            JulesoknadkandidatDAO.Julesoknadkandidat(
                julesoknadkandidatId = resultSet.getString("id"),
                sykepengesoknadUuid = resultSet.getString("sykepengesoknad_uuid"),
            )
        }
    }

    override fun lagreJulesoknadkandidat(sykepengesoknadUuid: String) {
        namedParameterJdbcTemplate.update(
            """
                    INSERT INTO JULESOKNADKANDIDAT (SYKEPENGESOKNAD_UUID, OPPRETTET)
                    VALUES (:sykepengesoknadUuid, :opprettet)
                    ON CONFLICT DO NOTHING
                    """,
            MapSqlParameterSource()
                .addValue("sykepengesoknadUuid", sykepengesoknadUuid)
                .addValue("opprettet", OffsetDateTime.now()),
        )
    }

    override fun slettJulesoknadkandidat(julesoknadkandidatId: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM JULESOKNADKANDIDAT WHERE ID = :julesoknadkandidatId",
            MapSqlParameterSource()
                .addValue("julesoknadkandidatId", julesoknadkandidatId),
        )
    }
}
