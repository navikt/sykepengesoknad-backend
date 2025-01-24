package no.nav.helse.flex.repository

import no.nav.helse.flex.domain.Soknadsperiode
import no.nav.helse.flex.domain.Sykmeldingstype
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Optional

interface SoknadsperiodeDAO {
    fun lagreSoknadperioder(
        sykepengesoknadId: String,
        soknadPerioder: List<Soknadsperiode>,
    )

    fun finnSoknadPerioder(sykepengesoknadIds: Set<String>): HashMap<String, MutableList<Soknadsperiode>>

    fun slettSoknadPerioder(sykepengesoknadId: String)
}

@Service
@Transactional
@Repository
@Profile("default")
class SoknadsperiodeDAOImpl(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) : SoknadsperiodeDAO {
    override fun lagreSoknadperioder(
        sykepengesoknadId: String,
        soknadPerioder: List<Soknadsperiode>,
    ) {
        if (soknadPerioder.isEmpty()) {
            return
        }
        soknadPerioder.forEach { (fom, tom, grad, sykmeldingstype) ->
            namedParameterJdbcTemplate.update(
                """
                INSERT INTO soknadperiode (sykepengesoknad_id, fom, tom, grad, sykmeldingstype)
                VALUES (:sykepengesoknadId, :fom, :tom, :grad, :sykmeldingstype)
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("sykepengesoknadId", sykepengesoknadId)
                    .addValue("fom", fom)
                    .addValue("tom", tom)
                    .addValue("grad", grad)
                    .addValue("sykmeldingstype", sykmeldingstype?.name),
            )
        }
    }

    override fun finnSoknadPerioder(sykepengesoknadIds: Set<String>): HashMap<String, MutableList<Soknadsperiode>> {
        val unMapped =
            sykepengesoknadIds.chunked(1000).map {
                namedParameterJdbcTemplate.query(
                    """
                    SELECT * FROM soknadperiode
                    WHERE sykepengesoknad_id IN (:sykepengesoknadId)
                    ORDER BY fom
                    """.trimIndent(),
                    MapSqlParameterSource()
                        .addValue("sykepengesoknadId", it),
                ) { resultSet, _ ->
                    Pair(
                        resultSet.getString("SYKEPENGESOKNAD_ID"),
                        Soknadsperiode(
                            resultSet.getObject("fom", LocalDate::class.java),
                            resultSet.getObject("tom", LocalDate::class.java),
                            resultSet.getInt("grad"),
                            Optional.ofNullable(resultSet.getString("sykmeldingstype"))
                                .map { Sykmeldingstype.valueOf(it) }.orElse(null),
                        ),
                    )
                }
            }
                .flatten()
                .sortedBy { it.second.fom }
        val ret = HashMap<String, MutableList<Soknadsperiode>>()
        unMapped.forEach {
            val lista = ret[it.first]
            if (lista != null) {
                lista.add(it.second)
            } else {
                ret[it.first] = mutableListOf(it.second)
            }
        }
        return ret
    }

    override fun slettSoknadPerioder(sykepengesoknadId: String) {
        namedParameterJdbcTemplate.update(
            """
            DELETE FROM soknadperiode
            WHERE sykepengesoknad_id = :sykepengesoknadId
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("sykepengesoknadId", sykepengesoknadId),
        )
    }
}
