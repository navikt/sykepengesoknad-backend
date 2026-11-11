package no.nav.helse.flex.forskuttering

import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfoOffset
import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfoOffsetId
import org.springframework.stereotype.Repository

interface ForskutteringEsyfoCheckpoint {
    fun lasOgHentOffset(
        topic: String,
        partition: Int,
    ): Long

    fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    )
}

@Repository("forskutteringEsyfoCheckpoint")
class DatabaseForskutteringEsyfoCheckpoint(
    private val repository: ForskutteringEsyfoOffsetRepository,
) : ForskutteringEsyfoCheckpoint {
    override fun lasOgHentOffset(
        topic: String,
        partition: Int,
    ): Long {
        repository.opprettCheckpoint(topic, partition)
        return repository.hentOffsetForOppdatering(topic, partition) ?: error("Mangler checkpoint etter opprettelse")
    }

    override fun lagreOffset(
        topic: String,
        partition: Int,
        offset: Long,
    ) {
        repository.save(ForskutteringEsyfoOffset(ForskutteringEsyfoOffsetId(topic, partition), offset))
    }
}
