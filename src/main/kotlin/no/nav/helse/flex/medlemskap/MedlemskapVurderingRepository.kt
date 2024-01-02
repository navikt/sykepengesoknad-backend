package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.util.serialisertTilString
import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate

@Repository
interface MedlemskapVurderingRepository : CrudRepository<MedlemskapVurderingDbRecord, String> {
    // Spør på 'fom' og 'tom' i tillegg til 'sykepengesoknadId' siden "klipp" av søknaden gjør at de blir hentet
    // ny medlemskapsvurdering en eller flere ganger.
    fun findBySykepengesoknadIdAndFomAndTom(
        sykepengesoknadId: String,
        fom: LocalDate,
        tom: LocalDate,
    ): MedlemskapVurderingDbRecord?

    @Modifying
    @Query("DELETE FROM medlemskap_vurdering WHERE sykepengesoknad_id = :sykepengesoknadId")
    fun deleteBySykepengesoknadId(sykepengesoknadId: String): Long

    @Modifying
    @Query("DELETE FROM medlemskap_vurdering WHERE fnr = :fnr")
    fun deleteByFnr(fnr: String): Long
}

@Table("medlemskap_vurdering")
data class MedlemskapVurderingDbRecord(
    @Id
    val id: String? = null,
    val timestamp: Instant,
    val svartid: Long,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val svartype: String,
    val sporsmal: PGobject? = null,
    val sykepengesoknadId: String,
)

fun Any.tilPostgresJson(): PGobject =
    PGobject().apply {
        type = "json"
        value = this@tilPostgresJson.serialisertTilString()
    }
