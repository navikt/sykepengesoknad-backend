package no.nav.helse.flex.forskuttering

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
    private val repository: ForskutteringEsyfoRepository,
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
        repository.lagreOffset(topic, partition, offset)
    }
}
