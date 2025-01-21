package no.nav.helse.flex.frisktilarbeid

import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

@Component
interface FriskTilArbeidRepository : CrudRepository<FriskTilArbeidDbRecord, String>

@Table("frisk_til_arbeid")
data class FriskTilArbeidDbRecord(
    @Id
    val id: String? = null,
    val timestamp: Instant,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val begrunnelse: String,
    val vedtakStatus: PGobject? = null,
)
