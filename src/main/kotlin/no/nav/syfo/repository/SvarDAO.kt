package no.nav.syfo.repository

import no.nav.syfo.domain.Svar
import no.nav.syfo.domain.SvarAvgittAv
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.util.EnumUtil.konverter
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils.isEmpty
import java.lang.Long.parseLong
import java.util.ArrayList
import java.util.HashMap
import java.util.Optional.ofNullable

@Service
@Transactional
@Repository
class SvarDAO(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {

    fun finnSvar(sporsmalIder: Set<String>): HashMap<String, MutableList<Svar>> {
        val svarMap = HashMap<String, MutableList<Svar>>()
        sporsmalIder.chunked(1000).forEach {
            namedParameterJdbcTemplate.query(
                "SELECT * FROM SVAR " +
                    "WHERE SPORSMAL_ID IN (:sporsmalIder) " +
                    "ORDER BY SVAR_ID",

                MapSqlParameterSource()
                    .addValue("sporsmalIder", it)

            ) { resultSet ->
                val sporsmalId = resultSet.getString("SPORSMAL_ID")
                svarMap.computeIfAbsent(sporsmalId) { ArrayList() }
                svarMap[sporsmalId]!!.add(
                    Svar(
                        id = resultSet.getString("SVAR_ID"),
                        verdi = resultSet.getString("VERDI"),
                        avgittAv = konverter(SvarAvgittAv::class.java, resultSet.getString("AVGITT_AV"))
                    )
                )
            }
        }
        return svarMap
    }

    fun lagreSvar(sporsmalId: Long, svar: Svar?) {
        @Suppress("DEPRECATION")
        if (svar == null || isEmpty(svar.verdi)) {
            return
        }
        namedParameterJdbcTemplate.update(
            "INSERT INTO SVAR (SVAR_ID, SPORSMAL_ID, VERDI, AVGITT_AV) VALUES (SVAR_ID_SEQ.NEXTVAL, :sporsmalId, :verdi, :avgittAv)",

            MapSqlParameterSource()
                .addValue("sporsmalId", sporsmalId)
                .addValue("verdi", svar.verdi)
                .addValue("avgittAv", ofNullable(svar.avgittAv).map { it.name }.orElse(null))
        )
    }

    fun slettSvar(sykepengesoknadUUID: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM SVAR WHERE SVAR_ID IN (SELECT SVAR_ID FROM SVAR INNER JOIN SPORSMAL ON SVAR.SPORSMAL_ID = SPORSMAL.SPORSMAL_ID INNER JOIN SYKEPENGESOKNAD ON SPORSMAL.SYKEPENGESOKNAD_ID = SYKEPENGESOKNAD.SYKEPENGESOKNAD_ID WHERE SYKEPENGESOKNAD_UUID = :soknadUUID)",
            MapSqlParameterSource()
                .addValue("soknadUUID", sykepengesoknadUUID)
        )
    }

    fun slettSvar(sporsmalIder: List<Long>) {
        if (sporsmalIder.isEmpty()) {
            return
        }

        sporsmalIder.chunked(1000).forEach {
            namedParameterJdbcTemplate.update(
                "DELETE FROM SVAR WHERE SPORSMAL_ID IN (:sporsmalIder)",

                MapSqlParameterSource()
                    .addValue("sporsmalIder", it)
            )
        }
    }

    fun overskrivSvar(sykepengesoknad: Sykepengesoknad) {
        val alleSporsmalOgUndersporsmal = sykepengesoknad.alleSporsmalOgUndersporsmal()
        slettSvar(alleSporsmalOgUndersporsmal.mapNotNull { it.id }.map { parseLong(it) })

        alleSporsmalOgUndersporsmal
            .forEach { sporsmal ->
                sporsmal.svar
                    .forEach { svar -> lagreSvar(parseLong(sporsmal.id!!), svar) }
            }
    }
}
