package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.domain.Periode
import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

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

    fun findByFnrIn(fnrs: List<String>): List<FriskTilArbeidVedtakDbRecord>
}

@Table("frisk_til_arbeid_vedtak")
data class FriskTilArbeidVedtakDbRecord(
    @Id
    val id: String? = null,
    val vedtakUuid: String,
    val key: String,
    val opprettet: Instant,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val vedtak: PGobject? = null,
    val behandletStatus: BehandletStatus,
    val behandletTidspunkt: Instant? = null,
    val avsluttetTidspunkt: Instant? = null,
    val ignorerArbeidssokerregister: Boolean? = null,
)

fun FriskTilArbeidVedtakDbRecord.sjekkArbeidssokerregisteret() = ignorerArbeidssokerregister != true

fun FriskTilArbeidVedtakDbRecord.tilPeriode(): Periode {
    fun avsluttetEllerTom(): LocalDate {
//        if (avsluttetTidspunkt != null) {
//            return avsluttetTidspunkt.tilOsloLocalDateTime().toLocalDate()
//        }
        return tom
    }
    return Periode(fom, avsluttetEllerTom())
}

enum class BehandletStatus {
    NY,
    BEHANDLET,
    OVERLAPP,
    OVERLAPP_OK,
    INGEN_ARBEIDSSOKERPERIODE,
    SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET,
}
