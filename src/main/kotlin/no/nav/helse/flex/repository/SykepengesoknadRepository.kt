package no.nav.helse.flex.repository

import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository

@Repository
interface SykepengesoknadRepository : CrudRepository<SykepengesoknadDbRecord, String> {

    fun findBySykepengesoknadUuid(sykepengesoknadUuid: String): SykepengesoknadDbRecord?
    fun findBySykepengesoknadUuidIn(sykepengesoknadUuid: List<String>): List<SykepengesoknadDbRecord>
}
