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
    // Uten låser gir dette samme svar som spørringen: eldste usendte rad per fnr, så lenge den ikke venter på nytt forsøk.
    override fun finnEldsteLedigeRadOgLas(): OutboxDbRecord? {
        val nå = Instant.now()
        val eldsteFørst = compareBy<OutboxDbRecord>({ it.opprettetTimestamp }, { it.id })
        return findAll()
            .filter { it.sendtTimestamp == null }
            .groupBy { it.fnr }
            .values
            .map { rader -> rader.minWith(eldsteFørst) }
            .filter { it.nesteForsokTimestamp?.isAfter(nå) != true }
            .minWithOrNull(eldsteFørst)
    }
}
