package no.nav.helse.flex.repository

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Sykepengesoknad
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
@Repository
class SvarDAO(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate) {
    fun finnSvar(sporsmalIder: Set<String>): HashMap<String, MutableList<Svar>> {
        val svarMap = HashMap<String, MutableList<Svar>>()
        sporsmalIder.chunked(1000).forEach {
            namedParameterJdbcTemplate.query(
                """
                SELECT * FROM svar
                WHERE sporsmal_id IN (:sporsmalIder)
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("sporsmalIder", it),
            ) { resultSet ->
                val sporsmalId = resultSet.getString("sporsmal_id")
                svarMap.computeIfAbsent(sporsmalId) { ArrayList() }
                svarMap[sporsmalId]!!.add(
                    Svar(
                        id = resultSet.getString("id"),
                        verdi = resultSet.getString("verdi"),
                    ),
                )
            }
        }
        return svarMap
    }

    fun lagreSvar(
        sporsmalId: String,
        svar: Svar?,
    ) {
        fun isEmpty(str: String?): Boolean {
            return str == null || "" == str
        }
        if (svar == null || isEmpty(svar.verdi)) {
            return
        }
        namedParameterJdbcTemplate.update(
            """
            INSERT INTO svar (sporsmal_id, verdi) 
            VALUES (:sporsmalId, :verdi)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("sporsmalId", sporsmalId)
                .addValue("verdi", svar.verdi),
        )
    }

    fun slettSvar(sykepengesoknadUUID: String) {
        namedParameterJdbcTemplate.update(
            """
            DELETE FROM svar
            WHERE svar.id IN 
            (
                SELECT svar.id FROM svar 
                INNER JOIN sporsmal ON svar.sporsmal_id = sporsmal.id
                INNER JOIN sykepengesoknad ON sporsmal.sykepengesoknad_id = sykepengesoknad.id
                WHERE sykepengesoknad_uuid = :soknadUUID
            )
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("soknadUUID", sykepengesoknadUUID),
        )
    }

    fun slettSvar(sporsmalIder: List<String>) {
        if (sporsmalIder.isEmpty()) {
            return
        }

        sporsmalIder.chunked(1000).forEach {
            namedParameterJdbcTemplate.update(
                "DELETE FROM svar WHERE sporsmal_id IN (:sporsmalIder)",
                MapSqlParameterSource()
                    .addValue("sporsmalIder", it),
            )
        }
    }

    fun slettSvar(
        sporsmalId: String,
        svarId: String,
    ) {
        namedParameterJdbcTemplate.update(
            "DELETE FROM svar WHERE sporsmal_id = :sporsmalId AND id = :svarId",
            MapSqlParameterSource()
                .addValue("sporsmalId", sporsmalId)
                .addValue("svarId", svarId),
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
