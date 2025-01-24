package no.nav.helse.flex.fakes

import no.nav.helse.flex.repository.RedusertVenteperiodeDbRecord
import no.nav.helse.flex.repository.RedusertVenteperiodeRepository
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.util.*

@Repository
@Profile("fakes")
class RedusertVenteperiodeRepositoryFake : RedusertVenteperiodeRepository {
    override fun insert(sykmeldingId: String) {
        TODO("Not yet implemented")
    }

    override fun existsBySykmeldingId(sykmeldingId: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun <S : RedusertVenteperiodeDbRecord?> save(entity: S & Any): S & Any {
        TODO("Not yet implemented")
    }

    override fun <S : RedusertVenteperiodeDbRecord?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<RedusertVenteperiodeDbRecord> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<RedusertVenteperiodeDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<RedusertVenteperiodeDbRecord> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: RedusertVenteperiodeDbRecord) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<RedusertVenteperiodeDbRecord>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll() {
        TODO("Not yet implemented")
    }
}
