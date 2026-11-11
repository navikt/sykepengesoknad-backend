package no.nav.helse.flex.fakes

import no.nav.helse.flex.forskuttering.ForskutteringEsyfoOffsetRepository
import no.nav.helse.flex.forskuttering.ForskutteringEsyfoRepository
import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfo
import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfoOffset
import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfoOffsetId
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
    fun forskutteringEsyfoOffsetRepository(): ForskutteringEsyfoOffsetRepositoryFake = ForskutteringEsyfoOffsetRepositoryFake()
}

class ForskutteringEsyfoRepositoryFake :
    InMemoryCrudRepository<ForskutteringEsyfo, String>(
        getId = { it.id },
        copyWithId = { forskuttering, id -> forskuttering.copy(id = id) },
        generateId = { UUID.randomUUID().toString() },
    ),
    ForskutteringEsyfoRepository {
    override fun deleteByNarmesteLederId(narmesteLederId: UUID) {
        val eksisterende = findByNarmesteLederId(narmesteLederId) ?: return
        delete(eksisterende)
    }

    override fun findByNarmesteLederId(narmesteLederId: UUID) = store.values.find { it.narmesteLederId == narmesteLederId }
}

class ForskutteringEsyfoOffsetRepositoryFake :
    InMemoryCrudRepository<ForskutteringEsyfoOffset, ForskutteringEsyfoOffsetId>(
        getId = { it.id },
        copyWithId = { offset, id -> offset.copy(id = id) },
        generateId = { error("Offset-ID må oppgis") },
    ),
    ForskutteringEsyfoOffsetRepository {
    override fun opprettCheckpoint(
        topic: String,
        partition: Int,
    ): Int {
        val id = ForskutteringEsyfoOffsetId(topic, partition)
        return if (store.putIfAbsent(id, ForskutteringEsyfoOffset(id, -1L)) == null) 1 else 0
    }

    override fun hentOffsetForOppdatering(
        topic: String,
        partition: Int,
    ): Long? = store[ForskutteringEsyfoOffsetId(topic, partition)]?.lastProcessedOffset
}
