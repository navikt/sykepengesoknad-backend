package no.nav.helse.flex.forskuttering

import no.nav.helse.flex.forskuttering.domain.ForskutteringEsyfo
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ForskutteringEsyfoRepository : CrudRepository<ForskutteringEsyfo, String> {
    fun deleteByNarmesteLederId(narmesteLederId: UUID)

    fun findByNarmesteLederId(narmesteLederId: UUID): ForskutteringEsyfo?
}
