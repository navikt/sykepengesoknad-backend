package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.util.serialisertTilString
import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.LocalDate
import java.util.*

@Repository
interface MedlemskapVurderingRepository : CrudRepository<MedlemskapVurderingDbRecord, String>

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
    val sykepengesoknadId: String
)

fun Any.tilPostgresJson(): PGobject =
    PGobject().apply { type = "json"; value = this@tilPostgresJson.serialisertTilString() }
