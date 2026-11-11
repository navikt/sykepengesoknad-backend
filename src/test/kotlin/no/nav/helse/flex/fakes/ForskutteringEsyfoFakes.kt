package no.nav.helse.flex.fakes

import no.nav.helse.flex.forskuttering.ForskutteringEsyfoCheckpoint
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

    @Bean
    fun forskutteringEsyfoCheckpoint(): ForskutteringEsyfoCheckpointFake = ForskutteringEsyfoCheckpointFake()
}

class ForskutteringEsyfoRepositoryFake :
    InMemoryCrudRepository<ForskutteringEsyfo, String>(
        getId = { it.id },
        copyWithId = { forskuttering, id -> forskuttering.copy(id = id) },
        generateId = { UUID.randomUUID().toString() },
    ),
    ForskutteringEsyfoRepository {
    override fun lagre(forskuttering: ForskutteringEsyfo): Int {
        val eksisterende = findByNarmesteLederId(forskuttering.narmesteLederId)
        save(forskuttering.copy(id = eksisterende?.id))
        return 1
    }

    override fun slett(narmesteLederId: UUID): Int {
        val eksisterende = findByNarmesteLederId(narmesteLederId) ?: return 0
        delete(eksisterende)
        return 1
    }

    override fun findByNarmesteLederId(narmesteLederId: UUID) = store.values.find { it.narmesteLederId == narmesteLederId }
}

class ForskutteringEsyfoCheckpointFake : ForskutteringEsyfoCheckpoint {
    private val offseter = mutableMapOf<Pair<String, Int>, Long>()

    override fun lasOgHentOffset(
        topic: String,
        partition: Int,
    ): Long = offseter.getOrDefault(topic to partition, -1L)

    override fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    ) {
        offseter[topic to partition] = offset
    }

    fun reset() {
        offseter.clear()
    }
}
