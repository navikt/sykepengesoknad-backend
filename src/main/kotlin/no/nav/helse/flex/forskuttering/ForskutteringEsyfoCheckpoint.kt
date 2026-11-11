package no.nav.helse.flex.forskuttering

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

interface ForskutteringEsyfoCheckpoint {
    fun lasOgHentOffset(
        topic: String,
        partition: Int,
    ): Long

    fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    )
}

@Repository("forskutteringEsyfoCheckpoint")
class DatabaseForskutteringEsyfoCheckpoint(
    private val jdbcTemplate: JdbcTemplate,
) : ForskutteringEsyfoCheckpoint {
    override fun lasOgHentOffset(
        topic: String,
        partition: Int,
    ): Long {
        jdbcTemplate.update(
            """INSERT INTO forskuttering_esyfo_checkpoint (topic, partition)
               VALUES (?, ?) ON CONFLICT (topic, partition) DO NOTHING""",
            topic,
            partition,
        )
        return jdbcTemplate.queryForObject(
            """SELECT last_processed_offset FROM forskuttering_esyfo_checkpoint
               WHERE topic = ? AND partition = ? FOR UPDATE""",
            Long::class.java,
            topic,
            partition,
        ) ?: error("Mangler checkpoint etter opprettelse")
    }

    override fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    ) {
        jdbcTemplate.update(
            """UPDATE forskuttering_esyfo_checkpoint SET last_processed_offset = ?
               WHERE topic = ? AND partition = ?""",
            offset,
            topic,
            partition,
        )
    }
}
