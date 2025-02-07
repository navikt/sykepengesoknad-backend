package no.nav.helse.flex.frisktilarbeid

import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.OffsetDateTime

@Component
interface FriskTilArbeidRepository : CrudRepository<FriskTilArbeidVedtakDbRecord, String> {
    @Query(
        """
        SELECT * 
        FROM frisk_til_arbeid_vedtak 
        WHERE behandlet_status = 'NY' 
        ORDER BY opprettet ASC 
        LIMIT :antallVedtak
        """,
    )
    fun finnVedtakSomSkalBehandles(antallVedtak: Int): List<FriskTilArbeidVedtakDbRecord>

    @Modifying
    @Query("DELETE FROM frisk_til_arbeid_vedtak WHERE fnr = :fnr")
    fun deleteByFnr(fnr: String): Long

    fun findByFnr(fnr: String): List<FriskTilArbeidVedtakDbRecord>
}

@Table("frisk_til_arbeid_vedtak")
data class FriskTilArbeidVedtakDbRecord(
    @Id
    val id: String? = null,
    val vedtakUuid: String,
    val key: String,
    val opprettet: OffsetDateTime,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val vedtak: PGobject? = null,
    val behandletStatus: BehandletStatus,
    val behandletTidspunkt: OffsetDateTime? = null,
)

enum class BehandletStatus {
    NY,
    BEHANDLET,
}
