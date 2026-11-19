package no.nav.helse.flex.outbox

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface OutboxDbRepository : CrudRepository<OutboxDbRecord, Long> {
    /**
     * Finner først alle fnr med usendte meldinger, sortert med eldste først, og med id som tiebreak.
     * Materialized sikrer at denne delen kjøres først, uten å inlines, så vi ikke låser alle fnr-ene.
     * Så går vi gjennom lista til vi finner et ulåst fnr, og låser kun det.
     * Låsen bruker to int-nøkler med hashtext('outbox') som første nøkkel. Da kolliderer den ikke med
     * bigint-låsene i LockRepository.
     *
     * Radene må hentes med en egen spørring etter at låsen er tatt, se [finnUsendteForFnr]. Snapshotet til
     * denne spørringen er tatt før låsen, og kan derfor vise rader som en annen instans har sendt og committet
     * før den slapp låsen.
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
    SELECT fnr
    FROM kandidater
    WHERE pg_try_advisory_xact_lock(hashtext('outbox'), hashtext(fnr))
    LIMIT 1
    """,
    )
    fun lasEldsteLedigeFnr(): String?

    @Query(
        """
    SELECT *
    FROM outbox
    WHERE fnr = :fnr
      AND sendt_timestamp IS NULL
    ORDER BY opprettet_timestamp, id
    """,
    )
    fun finnUsendteForFnr(fnr: String): List<OutboxDbRecord>
}
