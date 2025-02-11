package no.nav.helse.flex.fakes

import no.nav.helse.flex.repository.SporsmalDbRecord
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.CrudRepository
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile("fakes")
@Primary
class SporsmalRepositoryFake :
    InMemoryCrudRepository<SporsmalDbRecord, String>(
        getId = { it.id },
        copyWithId = { record, newId ->
            record.copy(id = newId)
        },
        generateId = { UUID.randomUUID().toString() },
    ),
    CrudRepository<SporsmalDbRecord, String> {
    fun lagreSporsmal(sporsmal: List<SporsmalDbRecord>) {
        sporsmal.forEach { store[it.id] = it }
    }

    fun slettSporsmalOgSvar(sykepengesoknadIder: List<String>) {
        store.values.removeIf { it.sykepengesoknadId in sykepengesoknadIder }
    }
}
