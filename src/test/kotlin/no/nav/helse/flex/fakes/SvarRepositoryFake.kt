package no.nav.helse.flex.fakes

import no.nav.helse.flex.repository.SvarDbRecord
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile("fakes")
@Primary
class SvarRepositoryFake :
    InMemoryCrudRepository<SvarDbRecord, String>(
        getId = { it.id },
        copyWithId = { record, newId ->
            record.copy(id = newId)
        },
        generateId = { UUID.randomUUID().toString() },
    ),
    CrudRepository<SvarDbRecord, String> {
    fun lagreSvar(svarDbRecords: List<SvarDbRecord>) {
        svarDbRecords.forEach { store[it.id ?: throw RuntimeException("Mangler id")] = it }
    }

    fun slettSvar(sporsmalIder: List<String>) {
        findAll().filter { it.sporsmalId in sporsmalIder }.forEach { delete(it) }
    }
}
