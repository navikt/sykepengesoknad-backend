package no.nav.helse.flex.aktivering

import org.springframework.data.annotation.Id
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.relational.core.mapping.Table
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
interface RetryRepository : CrudRepository<RetryRecord, String> {
    @Query(
        """
        INSERT INTO aktivering_retry_count (sykepengesoknad_uuid, retry_count, first_retry, last_retry)
        VALUES (:id, 1, :now, :now)
        ON CONFLICT (sykepengesoknad_uuid) DO UPDATE 
        SET retry_count = aktivering_retry_count.retry_count + 1, 
            last_retry = :now
        RETURNING retry_count
        """,
    )
    fun inkrementerRetries(
        id: String,
        now: Instant = Instant.now(),
    ): Int
}

@Table("aktivering_retry_count")
data class RetryRecord(
    @Id
    val sykepengesoknadUuid: String,
    val retryCount: Int,
    val firstRetry: Instant,
    val lastRetry: Instant,
)
