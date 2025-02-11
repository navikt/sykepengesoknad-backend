package no.nav.helse.flex.fakes

import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
@Profile("fakes")
@Primary
class SykepengesoknadRepositoryFake :
    InMemoryCrudRepository<SykepengesoknadDbRecord, String>(
        getId = { it.id },
        copyWithId = { record, newId ->
            record.copy(id = newId)
        },
        generateId = { UUID.randomUUID().toString() },
    ),
    SykepengesoknadRepository {
    fun lagreSoknad(soknad: SykepengesoknadDbRecord) {
        store[soknad.id ?: throw RuntimeException("Mangler id")] = soknad
    }

    override fun findBySykepengesoknadUuid(sykepengesoknadUuid: String): SykepengesoknadDbRecord? {
        return findAll().firstOrNull { it.sykepengesoknadUuid == sykepengesoknadUuid }
    }

    override fun findByFriskTilArbeidVedtakId(vedtakId: String): List<SykepengesoknadDbRecord> {
        return findAll().filter { it.friskTilArbeidVedtakId == vedtakId }
    }

    override fun findBySykepengesoknadUuidIn(sykepengesoknadUuid: List<String>): List<SykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findByFnrIn(fnrs: List<String>): List<SykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findBySykmeldingUuid(sykmeldingUuid: String): List<SykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun settErAktivertJulesoknadKandidat(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun erAktivertJulesoknadKandidat(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findEldsteSoknaden(
        identer: List<String>,
        fom: LocalDate?,
    ): String? {
        TODO("Not yet implemented")
    }

    override fun finnSoknaderSomSkalAktiveres(now: LocalDate): List<SykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }
}
