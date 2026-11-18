package no.nav.helse.flex.fakes

import no.nav.helse.flex.outbox.OutboxDbRecord
import no.nav.helse.flex.outbox.OutboxDbRepository
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

private val nesteOutboxId = AtomicLong()

@Repository
@Profile("fakes")
@Primary
class OutboxDbRepositoryFake :
    InMemoryCrudRepository<OutboxDbRecord, Long>(
        getId = { it.id },
        copyWithId = { record, newId -> record.copy(id = newId) },
        generateId = { nesteOutboxId.incrementAndGet() },
    ),
    OutboxDbRepository {
    override fun lasEldsteLedigeFnr(): String? =
        findAll()
            .filter { it.sendtTimestamp == null }
            .minWithOrNull(compareBy({ it.opprettetTimestamp }, { it.id }))
            ?.fnr

    override fun finnUsendteForFnr(fnr: String): List<OutboxDbRecord> =
        findAll()
            .filter { it.fnr == fnr && it.sendtTimestamp == null }
            .sortedWith(compareBy({ it.opprettetTimestamp }, { it.id }))

    override fun markerSomSendt(
        id: Long,
        sendtTimestamp: Instant,
    ): Int {
        var oppdatert = 0
        store.computeIfPresent(id) { _, record ->
            if (record.sendtTimestamp == null) {
                oppdatert = 1
                record.copy(sendtTimestamp = sendtTimestamp)
            } else {
                record
            }
        }
        return oppdatert
    }
}
