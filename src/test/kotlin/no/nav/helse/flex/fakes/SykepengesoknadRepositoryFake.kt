package no.nav.helse.flex.fakes

import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
@Profile("fakes")
class SykepengesoknadRepositoryFake : SykepengesoknadRepository {
    override fun findBySykepengesoknadUuid(sykepengesoknadUuid: String): SykepengesoknadDbRecord? {
        TODO("Not yet implemented")
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

    override fun <S : SykepengesoknadDbRecord?> save(entity: S & Any): S & Any {
        TODO("Not yet implemented")
    }

    override fun <S : SykepengesoknadDbRecord?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<SykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<SykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<SykepengesoknadDbRecord> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: SykepengesoknadDbRecord) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<SykepengesoknadDbRecord>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll() {
        TODO("Not yet implemented")
    }
}
