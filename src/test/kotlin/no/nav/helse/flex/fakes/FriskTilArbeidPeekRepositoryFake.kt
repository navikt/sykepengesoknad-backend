package no.nav.helse.flex.fakes

import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidPeekDbRecord
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidPeekRepository
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile("fakes")
@Primary
class FriskTilArbeidPeekRepositoryFake :
    InMemoryCrudRepository<FriskTilArbeidPeekDbRecord, String>(
        getId = { it.id },
        copyWithId = { record, newId ->
            record.copy(id = newId)
        },
        generateId = { UUID.randomUUID().toString() },
    ),
    FriskTilArbeidPeekRepository
