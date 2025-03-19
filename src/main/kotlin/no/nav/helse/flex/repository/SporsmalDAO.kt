package no.nav.helse.flex.repository

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Svar
import no.nav.helse.flex.domain.Svartype
import no.nav.helse.flex.domain.Visningskriterie
import no.nav.helse.flex.util.objectMapper
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*
import kotlin.collections.ArrayList

interface SporsmalDAO {
    fun finnSporsmal(sykepengesoknadIds: Set<String>): HashMap<String, MutableList<Sporsmal>>

    fun populerMedSvar(svarMap: HashMap<String, MutableList<Svar>>)

    fun slettSporsmalOgSvar(soknadsIder: List<String>)

    fun slettEnkeltSporsmal(sporsmalsIder: List<String>)
}

@Service
@Transactional
@Repository
class SporsmalDAOPostgres(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate, private val svarDAO: SvarDAO) :
    SporsmalDAO {
    @WithSpan
    override fun finnSporsmal(sykepengesoknadIds: Set<String>): HashMap<String, MutableList<Sporsmal>> {
        val unMapped =
            sykepengesoknadIds.chunked(1000).map { id ->
                namedParameterJdbcTemplate.query<List<Pair<String, Sporsmal>>>(
                    """
                    SELECT * FROM sporsmal
                    WHERE sykepengesoknad_id IN (:sykepengesoknadIds)
                    """.trimIndent(),
                    MapSqlParameterSource().addValue("sykepengesoknadIds", id),
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
                        val sporsmalId = resultSet.getString("id")
                        val kriterie = resultSet.getString("kriterie_for_visning")
                        svarMap[sporsmalId] = ArrayList()
                        val sykepengesoknadId = resultSet.getString("sykepengesoknad_id")

                        val undersporsmal = ArrayList<Sporsmal>()
                        val sporsmal =
                            Sporsmal(
                                id = sporsmalId,
                                tag = resultSet.getString("tag"),
                                sporsmalstekst = resultSet.getString("tekst"),
                                undertekst = resultSet.getString("undertekst"),
                                svartype = Svartype.valueOf(resultSet.getString("svartype")),
                                min = resultSet.getString("min"),
                                max = resultSet.getString("max"),
                                metadata = resultSet.getString("metadata")?.let { objectMapper.readTree(it) },
                                kriterieForVisningAvUndersporsmal = if (kriterie == null) null else Visningskriterie.valueOf(kriterie),
                                svar = svarMap[sporsmalId]!!,
                                undersporsmal = undersporsmal,
                            )
                        val underSporsmalId = resultSet.getNullableString("under_sporsmal_id")
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

    override fun populerMedSvar(svarMap: HashMap<String, MutableList<Svar>>) {
        val svarFraBasen = svarDAO.finnSvar(svarMap.keys)
        svarFraBasen.forEach { (key, value) -> svarMap[key]!!.addAll(value) }
    }

    @WithSpan
    override fun slettSporsmalOgSvar(soknadsIder: List<String>) {
        if (soknadsIder.isEmpty()) {
            return
        }

        val sporsmalsIder =
            namedParameterJdbcTemplate.query(
                """
                SELECT id FROM sporsmal 
                WHERE sykepengesoknad_id IN (:soknadsIder)
                """.trimIndent(),
                MapSqlParameterSource()
                    .addValue("soknadsIder", soknadsIder),
            ) { row, _ -> row.getString("id") }

        svarDAO.slettSvar(sporsmalsIder)

        namedParameterJdbcTemplate.update(
            """
            DELETE FROM sporsmal 
            WHERE sykepengesoknad_id IN (:soknadsIder)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("soknadsIder", soknadsIder),
        )
    }

    @WithSpan
    override fun slettEnkeltSporsmal(sporsmalsIder: List<String>) {
        if (sporsmalsIder.isEmpty()) {
            return
        }

        namedParameterJdbcTemplate.update(
            """
            DELETE FROM sporsmal 
            WHERE id IN (:sporsmalsIder)
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("sporsmalsIder", sporsmalsIder),
        )
        svarDAO.slettSvar(sporsmalsIder)
    }
}
