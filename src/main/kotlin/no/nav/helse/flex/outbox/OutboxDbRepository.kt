package no.nav.helse.flex.outbox

import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface OutboxDbRepository : CrudRepository<OutboxDbRecord, Long> {
    /**
     * Låser og returnerer den eldste usendte raden som ikke har eldre usendte rader for samme fnr.
     *
     * Raden som er eldst for et fnr, fungerer som lås for hele fnr-et: Så lenge den er låst og usendt,
     * utelukker NOT EXISTS de nyere radene for fnr-et, og SKIP LOCKED hopper over den låste raden.
     * Når raden låses, sjekker Postgres WHERE på nytt mot siste committede versjon, så vi låser aldri
     * en rad som en annen instans allerede har sendt.
     *
     * Dette forutsetter at hver transaksjon bare sender én rad.
     */
    @Query(
        """
    SELECT *
    FROM outbox o
    WHERE o.sendt_timestamp IS NULL
      AND NOT EXISTS (
          SELECT 1
          FROM outbox eldre
          WHERE eldre.fnr = o.fnr
            AND eldre.sendt_timestamp IS NULL
            AND (eldre.opprettet_timestamp, eldre.id) < (o.opprettet_timestamp, o.id)
      )
    ORDER BY o.opprettet_timestamp, o.id
    LIMIT 1
    FOR UPDATE SKIP LOCKED
    """,
    )
    fun finnEldsteLedigeRadOgLas(): OutboxDbRecord?
}
