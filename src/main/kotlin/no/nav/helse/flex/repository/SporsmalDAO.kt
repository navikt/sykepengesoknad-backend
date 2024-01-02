package no.nav.helse.flex.repository

import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.collections.ArrayList

@Service
@Transactional
@Repository
class SporsmalDAO(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate, private val svarDAO: SvarDAO) {
    fun finnSporsmal(sykepengesoknadIds: Set<String>): HashMap<String, MutableList<Sporsmal>> {
        val unMapped =
            sykepengesoknadIds.chunked(1000).map {
                namedParameterJdbcTemplate.query<List<Pair<String, Sporsmal>>>(
                    "SELECT * FROM SPORSMAL " +
                        "WHERE SPORSMAL.SYKEPENGESOKNAD_ID in (:sykepengesoknadIds) ",
                    MapSqlParameterSource()
                        .addValue("sykepengesoknadIds", it),
                ) { resultSet ->
                    val svarMap = HashMap<String, MutableList<Svar>>()
                    val sporsmalList = ArrayList<Pair<String, Sporsmal>>()

                    data class SporsmalHelper(
                        val sporsmal: Sporsmal,
                        val sykepengesoknadId: String,
                        val underSporsmalId: String?,
                        val undersporsmal: ArrayList<Sporsmal>,
                    )

                    val sporsmalMap = HashMap<String, SporsmalHelper>()

                    while (resultSet.next()) {
                        val sporsmalId = resultSet.getString("ID")
                        val kriterie = resultSet.getString("KRITERIE_FOR_VISNING")
                        svarMap[sporsmalId] = ArrayList()
                        val sykepengesoknadId = resultSet.getString("SYKEPENGESOKNAD_ID")

                        val undersporsmal = ArrayList<Sporsmal>()
                        val sporsmal =
                            Sporsmal(
                                id = sporsmalId,
                                tag = resultSet.getString("TAG"),
                                sporsmalstekst = resultSet.getString("TEKST"),
                                undertekst = resultSet.getString("UNDERTEKST"),
                                svartype = Svartype.valueOf(resultSet.getString("SVARTYPE")),
                                min = resultSet.getString("MIN"),
                                max = resultSet.getString("MAX"),
                                kriterieForVisningAvUndersporsmal = if (kriterie == null) null else Visningskriterie.valueOf(kriterie),
                                svar = svarMap[sporsmalId]!!,
                                undersporsmal = undersporsmal,
                            )
                        val underSporsmalId = resultSet.getNullableString("UNDER_SPORSMAL_ID")
                        sporsmalMap[sporsmalId] = SporsmalHelper(sporsmal, sykepengesoknadId, underSporsmalId, undersporsmal)
                    }
                    sporsmalMap.values.forEach { spm ->
                        if (spm.underSporsmalId == null) {
                            sporsmalList.add(Pair(spm.sykepengesoknadId, spm.sporsmal))
                        } else {
                            sporsmalMap[spm.underSporsmalId]!!.undersporsmal.add(spm.sporsmal)
                        }
                    }
                    populerMedSvar(svarMap)
                    sporsmalList
                } ?: emptyList()
            }
                .flatten()
                .sortedBy { it.second.id }
        val ret = HashMap<String, MutableList<Sporsmal>>()
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

    private fun populerMedSvar(svarMap: HashMap<String, MutableList<Svar>>) {
        val svarFraBasen = svarDAO.finnSvar(svarMap.keys)
        svarFraBasen.forEach { (key, value) -> svarMap[key]!!.addAll(value) }
    }

    fun slettSporsmal(soknadsIder: List<String>) {
        if (soknadsIder.isEmpty()) {
            return
        }

        val sporsmalsIder =
            namedParameterJdbcTemplate.query(
                "SELECT id FROM sporsmal WHERE sykepengesoknad_id IN (:soknadsIder)",
                MapSqlParameterSource()
                    .addValue("soknadsIder", soknadsIder),
            ) { row, _ -> row.getString("ID") }

        svarDAO.slettSvar(sporsmalsIder)

        namedParameterJdbcTemplate.update(
            "DELETE FROM sporsmal WHERE sykepengesoknad_id IN (:soknadsIder)",
            MapSqlParameterSource()
                .addValue("soknadsIder", soknadsIder),
        )
    }

    fun slettEnkeltSporsmal(sporsmalsIder: List<String>) {
        if (sporsmalsIder.isEmpty()) {
            return
        }

        namedParameterJdbcTemplate.update(
            "DELETE FROM sporsmal WHERE id IN (:sporsmalsIder)",
            MapSqlParameterSource()
                .addValue("sporsmalsIder", sporsmalsIder),
        )
        svarDAO.slettSvar(sporsmalsIder)
    }
}
