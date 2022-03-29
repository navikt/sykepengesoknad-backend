package no.nav.syfo.repository

import no.nav.syfo.domain.Soknadsperiode
import no.nav.syfo.domain.Sykmeldingstype
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.Optional

@Service
@Transactional
@Repository
class SoknadsperiodeDAO(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {

    fun lagreSoknadperioder(sykepengesoknadId: Long, soknadPerioder: List<Soknadsperiode>) {
        if (soknadPerioder.isEmpty()) {
            return
        }
        soknadPerioder.forEach { (fom, tom, grad, sykmeldingstype) ->
            namedParameterJdbcTemplate.update(
                "INSERT INTO SOKNADPERIODE (SOKNADPERIODE_ID, SYKEPENGESOKNAD_ID, FOM, TOM, GRAD, SYKMELDINGSTYPE) " + "VALUES (SOKNADPERIODE_ID_SEQ.NEXTVAL, :sykepengesoknadId, :fom, :tom, :grad, :sykmeldingstype)",

                MapSqlParameterSource()
                    .addValue("sykepengesoknadId", sykepengesoknadId)
                    .addValue("fom", fom)
                    .addValue("tom", tom)
                    .addValue("grad", grad)
                    .addValue("sykmeldingstype", sykmeldingstype?.name)
            )
        }
    }

    fun finnSoknadPerioder(sykepengesoknadIds: Set<Long>): HashMap<Long, MutableList<Soknadsperiode>> {
        val unMapped = sykepengesoknadIds.chunked(1000).map {
            namedParameterJdbcTemplate.query(
                "SELECT * FROM SOKNADPERIODE " +
                    "WHERE SYKEPENGESOKNAD_ID in (:sykepengesoknadId) " +
                    "ORDER BY FOM",

                MapSqlParameterSource()
                    .addValue("sykepengesoknadId", it)

            ) { resultSet, _ ->
                Pair(
                    resultSet.getLong("SYKEPENGESOKNAD_ID"),
                    Soknadsperiode(
                        resultSet.getObject("FOM", LocalDate::class.java),
                        resultSet.getObject("TOM", LocalDate::class.java),
                        resultSet.getInt("GRAD"),
                        Optional.ofNullable(resultSet.getString("SYKMELDINGSTYPE"))
                            .map { Sykmeldingstype.valueOf(it) }.orElse(null)
                    )
                )
            }
        }
            .flatten()
            .sortedBy { it.second.fom }
        val ret = HashMap<Long, MutableList<Soknadsperiode>>()
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

    fun slettSoknadPerioder(sykepengesoknadId: Long) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM SOKNADPERIODE " + "WHERE SYKEPENGESOKNAD_ID = :sykepengesoknadId",

            MapSqlParameterSource()
                .addValue("sykepengesoknadId", sykepengesoknadId)
        )
    }
}
