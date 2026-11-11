package no.nav.helse.flex.forskuttering

import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfo
import org.springframework.data.jdbc.repository.query.Modifying
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ForskutteringEsyfoRepository : CrudRepository<ForskutteringEsyfo, String> {
    fun deleteByNarmesteLederId(narmesteLederId: UUID)

    fun findByNarmesteLederId(narmesteLederId: UUID): ForskutteringEsyfo?

    @Modifying
    @Query(
        """
        INSERT INTO forskuttering_esyfo_checkpoint (topic, partition)
        VALUES (:topic, :partition) ON CONFLICT (topic, partition) DO NOTHING
        """,
    )
    fun opprettCheckpoint(
        topic: String,
        partition: Int,
    ): Int

    @Query(
        """
        SELECT last_processed_offset FROM forskuttering_esyfo_checkpoint
        WHERE topic = :topic AND partition = :partition FOR UPDATE
        """,
    )
    fun hentOffsetForOppdatering(
        topic: String,
        partition: Int,
    ): Long?

    @Modifying
    @Query(
        """
        UPDATE forskuttering_esyfo_checkpoint SET last_processed_offset = :offset
        WHERE topic = :topic AND partition = :partition
        """,
    )
    fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    ): Int
}
