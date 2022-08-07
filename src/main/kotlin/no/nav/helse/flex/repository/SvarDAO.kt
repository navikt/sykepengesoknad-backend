package no.nav.helse.flex.repository

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.util.StringUtils.isEmpty
import java.util.ArrayList
import java.util.HashMap

@Service
@Transactional
@Repository
class SvarDAO(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {

    fun finnSvar(sporsmalIder: Set<String>): HashMap<String, MutableList<Svar>> {
        val svarMap = HashMap<String, MutableList<Svar>>()
        sporsmalIder.chunked(1000).forEach {
            namedParameterJdbcTemplate.query(
                "SELECT * FROM SVAR " +
                    "WHERE SPORSMAL_ID IN (:sporsmalIder) ",

                MapSqlParameterSource()
                    .addValue("sporsmalIder", it)

            ) { resultSet ->
                val sporsmalId = resultSet.getString("SPORSMAL_ID")
                svarMap.computeIfAbsent(sporsmalId) { ArrayList() }
                svarMap[sporsmalId]!!.add(
                    Svar(
                        id = resultSet.getString("ID"),
                        verdi = resultSet.getString("VERDI"),
                    )
                )
            }
        }
        return svarMap
    }

    fun lagreSvar(sporsmalId: String, svar: Svar?) {
        @Suppress("DEPRECATION")
        if (svar == null || isEmpty(svar.verdi)) {
            return
        }
        namedParameterJdbcTemplate.update(
            "INSERT INTO SVAR (SPORSMAL_ID, VERDI) VALUES (:sporsmalId, :verdi)",

            MapSqlParameterSource()
                .addValue("sporsmalId", sporsmalId)
                .addValue("verdi", svar.verdi)
        )
    }

    fun slettSvar(sykepengesoknadUUID: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM SVAR WHERE SVAR.ID IN (SELECT SVAR.ID FROM SVAR INNER JOIN SPORSMAL ON SVAR.SPORSMAL_ID = SPORSMAL.ID INNER JOIN SYKEPENGESOKNAD ON SPORSMAL.SYKEPENGESOKNAD_ID = SYKEPENGESOKNAD.ID WHERE SYKEPENGESOKNAD_UUID = :soknadUUID)",
            MapSqlParameterSource()
                .addValue("soknadUUID", sykepengesoknadUUID)
        )
    }

    fun slettSvar(sporsmalIder: List<String>) {
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

    fun slettSvar(sporsmalId: String, svarId: String) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM SVAR WHERE SPORSMAL_ID = :sporsmalId AND ID = :svarId",

            MapSqlParameterSource()
                .addValue("sporsmalId", sporsmalId)
                .addValue("svarId", svarId)
        )
    }

    fun overskrivSvar(sykepengesoknad: Sykepengesoknad) {
        val alleSporsmalOgUndersporsmal = sykepengesoknad.alleSporsmalOgUndersporsmal()
        slettSvar(alleSporsmalOgUndersporsmal.mapNotNull { it.id })

        alleSporsmalOgUndersporsmal
            .forEach { sporsmal ->
                sporsmal.svar
                    .forEach { svar -> lagreSvar(sporsmal.id!!, svar) }
            }
    }

    fun overskrivSvar(sporsmal: List<Sporsmal>) {
        slettSvar(sporsmal.mapNotNull { it.id })

        sporsmal.forEach {
            it.svar.forEach { svar -> lagreSvar(it.id!!, svar) }
        }
    }
}
