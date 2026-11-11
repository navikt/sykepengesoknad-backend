package no.nav.helse.flex.fakes

import no.nav.helse.flex.forskuttering.ForskutteringEsyfoRepository
import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfo
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.util.UUID

@Configuration
@Profile("fakes")
class ForskutteringEsyfoFakes {
    @Bean
    fun forskutteringEsyfoRepository(): ForskutteringEsyfoRepositoryFake = ForskutteringEsyfoRepositoryFake()
}

class ForskutteringEsyfoRepositoryFake :
    InMemoryCrudRepository<ForskutteringEsyfo, String>(
        getId = { it.id },
        copyWithId = { forskuttering, id -> forskuttering.copy(id = id) },
        generateId = { UUID.randomUUID().toString() },
    ),
    ForskutteringEsyfoRepository {
    private val offseter = mutableMapOf<Pair<String, Int>, Long>()

    override fun deleteByNarmesteLederId(narmesteLederId: UUID) {
        val eksisterende = findByNarmesteLederId(narmesteLederId) ?: return
        delete(eksisterende)
    }

    override fun findByNarmesteLederId(narmesteLederId: UUID) = store.values.find { it.narmesteLederId == narmesteLederId }

    override fun opprettCheckpoint(
        topic: String,
        partition: Int,
    ): Int = if (offseter.putIfAbsent(topic to partition, -1L) == null) 1 else 0

    override fun hentOffsetForOppdatering(
        topic: String,
        partition: Int,
    ): Long? = offseter[topic to partition]

    override fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    ): Int {
        if ((topic to partition) !in offseter) return 0
        offseter[topic to partition] = offset
        return 1
    }

    fun reset() {
        deleteAll()
        offseter.clear()
    }
}
