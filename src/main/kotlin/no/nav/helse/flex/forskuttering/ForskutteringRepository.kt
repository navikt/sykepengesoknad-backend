package no.nav.helse.flex.forskuttering

import no.nav.helse.flex.forskuttering.domain.Forskuttering
import org.springframework.data.jdbc.repository.query.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ForskutteringRepository : CrudRepository<Forskuttering, String> {
    fun findByNarmesteLederId(narmesteLederId: UUID): Forskuttering?

    @Query("SELECT * from forskuttering where bruker_fnr = :brukerFnr and orgnummer = :orgnummer ORDER BY aktiv_tom DESC NULLS FIRST LIMIT 1 ")
    fun finnForskuttering(brukerFnr: String, orgnummer: String): Forskuttering?
}
