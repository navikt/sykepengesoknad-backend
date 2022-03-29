package no.nav.syfo.testdata

import no.nav.syfo.repository.SykepengesoknadDAO
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Transactional
@Repository
class DatabaseReset(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val sykepengesoknadDAO: SykepengesoknadDAO
) {

    fun resetDatabase() {
        val aktorIder = namedParameterJdbcTemplate.query(
            "SELECT distinct(FNR) FROM SYKEPENGESOKNAD",
            MapSqlParameterSource()
        ) { resultSet, _ ->
            resultSet.getString("FNR")
        }

        if (aktorIder.size > 10) {
            throw RuntimeException("Sikker på at det er så mye testdata som ${aktorIder.size} i databasen")
        }

        aktorIder.forEach {
            sykepengesoknadDAO.nullstillSoknader(it)
        }
        namedParameterJdbcTemplate.update("DELETE FROM JULESOKNADKANDIDAT", MapSqlParameterSource())
    }
}
