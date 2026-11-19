package no.nav.helse.flex.fakes

import no.nav.helse.flex.outbox.OutboxDbRecord
import no.nav.helse.flex.outbox.OutboxDbRepository
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
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
    // Den eldste usendte raden totalt har aldri eldre usendte rader for samme fnr. Uten låser gir det samme svar som spørringen.
    override fun finnEldsteLedigeRadOgLas(): OutboxDbRecord? =
        findAll()
            .filter { it.sendtTimestamp == null }
            .minWithOrNull(compareBy({ it.opprettetTimestamp }, { it.id }))
}
