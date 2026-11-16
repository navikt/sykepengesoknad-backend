package no.nav.helse.flex.outbox

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface OutboxDbRepository : CrudRepository<OutboxDbRecord, Long> {
    /**
     * Finner først alle usendte meldinger, gruppert på fnr, sortert med eldste først, og med id som tiebreak.
     * Materialized sikrer at denne delen kjøres først, uten å inlines, så vi ikke låser alle fnr-ene.
     * Så går vi gjennom den grupperte lista til vi finner en ulåst fnr, og låser kun den.
     * Låsen bruker to int-nøkler med hashtext('outbox') som første nøkkel. Da kolliderer den ikke med
     * bigint-låsene i LockRepository.
     * Henter så alle usendte meldinger for det låste fnr-et sortert med eldste først, og med id som tiebreak.
     */
    @Query(
        """
    WITH kandidater AS MATERIALIZED (
        SELECT fnr
        FROM outbox
        WHERE sendt_timestamp IS NULL
        GROUP BY fnr
        ORDER BY MIN(opprettet_timestamp), MIN(id)
    )
    SELECT *
    FROM outbox
    WHERE sendt_timestamp IS NULL
      AND fnr = (
          SELECT fnr
          FROM kandidater
          WHERE pg_try_advisory_xact_lock(hashtext('outbox'), hashtext(fnr))
          LIMIT 1
      )
    ORDER BY opprettet_timestamp, id
    """,
    )
    fun finnOgLasUsendteForEldsteLedigeFnr(): List<OutboxDbRecord>
}
