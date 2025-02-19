package no.nav.helse.flex.fakes

import no.nav.helse.flex.repository.SoknadsperiodeDbRecord
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile("fakes")
@Primary
class SoknadsperiodeRepositoryFake :
    InMemoryCrudRepository<SoknadsperiodeDbRecord, String>(
        getId = { it.id },
        copyWithId = { record, newId ->
            record.copy(id = newId)
        },
        generateId = { UUID.randomUUID().toString() },
    ),
    CrudRepository<SoknadsperiodeDbRecord, String> {
    fun lagreSoknadsperioder(sporsmal: List<SoknadsperiodeDbRecord>) {
        sporsmal.forEach { store[it.id] = it }
    }

    fun slettPerioder(sykepengesoknadIder: List<String>) {
        store.values.removeIf { it.sykepengesoknadId in sykepengesoknadIder }
    }
}
