package no.nav.helse.flex.frisktilarbeid

import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
interface FriskTilArbeidPeekRepository : CrudRepository<FriskTilArbeidPeekDbRecord, String>

@Table("fta_peek")
data class FriskTilArbeidPeekDbRecord(
    @Id
    val id: String? = null,
    val fnr: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val vedtak: PGobject? = null,
)
