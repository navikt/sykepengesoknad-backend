package no.nav.syfo.repository

import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Svar
import no.nav.syfo.domain.Svartype
import no.nav.syfo.domain.Visningskriterie
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.*

@Service
@Transactional
@Repository
class SporsmalDAO(private val namedParameterJdbcTemplate: NamedParameterJdbcTemplate, private val svarDAO: SvarDAO) {

    fun finnSporsmal(sykepengesoknadIds: Set<Long>): HashMap<Long, MutableList<Sporsmal>> {

        val unMapped = sykepengesoknadIds.chunked(1000).map {
            namedParameterJdbcTemplate.query<List<Pair<Long, Sporsmal>>>(
                "SELECT * FROM SPORSMAL " +
                    "WHERE SPORSMAL.SYKEPENGESOKNAD_ID in (:sykepengesoknadIds) " +
                    "ORDER BY SPORSMAL.SPORSMAL_ID",

                MapSqlParameterSource()
                    .addValue("sykepengesoknadIds", it)

            ) { resultSet ->
                val svarMap = HashMap<String, MutableList<Svar>>()
                val sporsmalList = ArrayList<Pair<Long, Sporsmal>>()
                val sporsmalMap = HashMap<String, Sporsmal>()

                while (resultSet.next()) {
                    val sporsmalId = resultSet.getString("SPORSMAL_ID")
                    val kriterie = resultSet.getString("KRITERIE_FOR_VISNING")
                    svarMap[sporsmalId] = ArrayList()
                    val sykepengesoknadId = resultSet.getLong("SYKEPENGESOKNAD_ID")
                    val sporsmal = Sporsmal(
                        sporsmalId,
                        resultSet.getString("TAG"),
                        resultSet.getString("TEKST"),
                        resultSet.getString("UNDERTEKST"),
                        Svartype.valueOf(resultSet.getString("SVARTYPE")),
                        resultSet.getString("MIN"),
                        resultSet.getString("MAX"),
                        resultSet.getBoolean("PAVIRKER_ANDRE_SPORSMAL"),
                        if (kriterie == null) null else Visningskriterie.valueOf(kriterie),
                        svarMap[sporsmalId]!!,
                        ArrayList()
                    )
                    val underSporsmalId = resultSet.getString("UNDER_SPORSMAL_ID")
                    sporsmalMap[sporsmalId] = sporsmal
                    if (underSporsmalId == null) {
                        sporsmalList.add(Pair(sykepengesoknadId, sporsmal))
                    } else {
                        (sporsmalMap[underSporsmalId]!!.undersporsmal as ArrayList).add(sporsmal)
                    }
                }
                populerMedSvar(svarMap)
                sporsmalList
            } ?: emptyList()
        }
            .flatten()
            .sortedBy { it.second.id?.toLong() }
        val ret = HashMap<Long, MutableList<Sporsmal>>()
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

    fun lagreSporsmal(sykepengesoknadId: Long, sporsmal: Sporsmal, underSporsmalId: Long?): Sporsmal {
        return lagreSporsmal(sykepengesoknadId, null, sporsmal, underSporsmalId)
    }

    private fun lagreSporsmal(sykepengesoknadId: Long?, sykepengesoknadUuid: String?, sporsmal: Sporsmal, underSporsmalId: Long?): Sporsmal {
        val generatedKeyHolder = GeneratedKeyHolder()
        namedParameterJdbcTemplate.update(
            "INSERT INTO SPORSMAL(SPORSMAL_ID, SYKEPENGESOKNAD_ID, UNDER_SPORSMAL_ID, TEKST, UNDERTEKST, TAG, " +
                "SVARTYPE, MIN, MAX, KRITERIE_FOR_VISNING, PAVIRKER_ANDRE_SPORSMAL) " +

                "VALUES (SPORSMAL_ID_SEQ.NEXTVAL, " +
                "(SELECT SYKEPENGESOKNAD_ID " +
                "FROM SYKEPENGESOKNAD " +
                "WHERE SYKEPENGESOKNAD_ID = :sykepengesoknadId " +
                "OR SYKEPENGESOKNAD_UUID = :sykepengesoknadUuid), " +
                ":underSporsmalId, " +
                ":tekst, " +
                ":undertekst, " +
                ":tag, " +
                ":svartype, " +
                ":min, " +
                ":max, " +
                ":kriterie, " +
                ":pavirkerAndreSporsmal)",

            MapSqlParameterSource()
                .addValue("sykepengesoknadId", sykepengesoknadId)
                .addValue("sykepengesoknadUuid", sykepengesoknadUuid)
                .addValue("underSporsmalId", underSporsmalId)
                .addValue("tekst", sporsmal.sporsmalstekst)
                .addValue("undertekst", sporsmal.undertekst)
                .addValue("tag", sporsmal.tag)
                .addValue("svartype", sporsmal.svartype.name)
                .addValue("min", sporsmal.min)
                .addValue("max", sporsmal.max)
                .addValue(
                    "kriterie",
                    if (sporsmal.kriterieForVisningAvUndersporsmal == null)
                        null
                    else
                        sporsmal.kriterieForVisningAvUndersporsmal.name
                )
                .addValue("pavirkerAndreSporsmal", sporsmal.pavirkerAndreSporsmal),

            generatedKeyHolder,
            arrayOf("SPORSMAL_ID")
        )

        val sporsmalId = generatedKeyHolder.key!!.toLong()
        sporsmal.svar.forEach { svar -> svarDAO.lagreSvar(sporsmalId, svar) }
        val undersporsmal = sporsmal.undersporsmal.map { u -> lagreSporsmal(sykepengesoknadId, sykepengesoknadUuid, u, sporsmalId) }
        return sporsmal.copy(id = sporsmalId.toString(), undersporsmal = undersporsmal)
    }

    fun slettSporsmal(soknadsIder: List<Long>) {
        if (soknadsIder.isEmpty()) {
            return
        }

        val sporsmalsIder = namedParameterJdbcTemplate.query(
            "SELECT SPORSMAL_ID FROM SPORSMAL WHERE SYKEPENGESOKNAD_ID in (:soknadsIder)",

            MapSqlParameterSource()
                .addValue("soknadsIder", soknadsIder)

        ) { row, _ -> row.getLong("SPORSMAL_ID") }

        svarDAO.slettSvar(sporsmalsIder)

        namedParameterJdbcTemplate.update(
            "DELETE FROM SPORSMAL WHERE SYKEPENGESOKNAD_ID in (:soknadsIder)",

            MapSqlParameterSource()
                .addValue("soknadsIder", soknadsIder)
        )
    }

    fun oppdaterSporsmalstekst(nyttSporsmal: Sporsmal) {
        namedParameterJdbcTemplate.update(
            "UPDATE SPORSMAL SET TEKST = :sporsmalstekst, UNDERTEKST = :undertekst WHERE SPORSMAL_ID = :sporsmalId",

            MapSqlParameterSource()
                .addValue("sporsmalstekst", nyttSporsmal.sporsmalstekst)
                .addValue("undertekst", nyttSporsmal.undertekst)
                .addValue("sporsmalId", nyttSporsmal.id)
        )
    }

    fun oppdaterSporsmalGrense(sporsmalId: String, min: String) {
        namedParameterJdbcTemplate.update(
            "UPDATE SPORSMAL SET MIN = :min WHERE SPORSMAL_ID = :sporsmalId",

            MapSqlParameterSource()
                .addValue("min", min)
                .addValue("sporsmalId", sporsmalId)
        )
    }
}
