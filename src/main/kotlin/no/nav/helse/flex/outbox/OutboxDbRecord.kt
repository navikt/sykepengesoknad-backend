package no.nav.helse.flex.outbox

import org.postgresql.util.PGobject
import org.springframework.data.annotation.Id
import org.springframework.data.relational.core.mapping.Table
import java.time.Instant

@Table("outbox")
data class OutboxDbRecord(
    @Id
    val id: Long? = null,
    val type: OutboxType,
    val fnr: String,
    val payload: PGobject,
    val opprettetTimestamp: Instant,
    val sendtTimestamp: Instant?,
)
