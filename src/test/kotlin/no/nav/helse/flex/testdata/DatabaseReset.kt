package no.nav.helse.flex.testdata

import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.repository.KlippMetrikkRepository
import no.nav.helse.flex.repository.SykepengesoknadDAO
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Transactional
@Repository
@Profile("test")
class DatabaseReset(
    private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
    private val klippMetrikkRepository: KlippMetrikkRepository,
    private val medlemskapVurderingRepository: MedlemskapVurderingRepository,
) {
    fun resetDatabase() {
        val aktorIder =
            namedParameterJdbcTemplate.query(
                "SELECT distinct(FNR) FROM sykepengesoknad",
                MapSqlParameterSource(),
            ) { resultSet, _ ->
                resultSet.getString("FNR")
            }

        if (aktorIder.size > 10) {
            throw RuntimeException("Sikker på at det er så mye testdata som ${aktorIder.size} i databasen")
        }

        aktorIder.forEach {
            sykepengesoknadDAO.nullstillSoknader(it)
        }
        namedParameterJdbcTemplate.update("DELETE FROM julesoknadkandidat", MapSqlParameterSource())
        klippMetrikkRepository.deleteAll()
        medlemskapVurderingRepository.deleteAll()
    }
}
