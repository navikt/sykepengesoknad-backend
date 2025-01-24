package no.nav.helse.flex.fakes

import no.nav.helse.flex.medlemskap.MedlemskapVurderingDbRecord
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.util.*

@Repository
@Profile("fakes")
@Primary
class MedlemskapsrepositoryFake : MedlemskapVurderingRepository {
    override fun findBySykepengesoknadIdAndFomAndTom(
        sykepengesoknadId: String,
        fom: LocalDate,
        tom: LocalDate,
    ): MedlemskapVurderingDbRecord? {
        TODO("Not yet implemented")
    }

    override fun deleteBySykepengesoknadId(sykepengesoknadId: String): Long {
        TODO("Not yet implemented")
    }

    override fun deleteByFnr(fnr: String): Long {
        TODO("Not yet implemented")
    }

    override fun <S : MedlemskapVurderingDbRecord?> save(entity: S & Any): S & Any {
        TODO("Not yet implemented")
    }

    override fun <S : MedlemskapVurderingDbRecord?> saveAll(entities: MutableIterable<S>): MutableIterable<S> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<MedlemskapVurderingDbRecord> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAll(): MutableIterable<MedlemskapVurderingDbRecord> {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: MutableIterable<String>): MutableIterable<MedlemskapVurderingDbRecord> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: MedlemskapVurderingDbRecord) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: MutableIterable<String>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: MutableIterable<MedlemskapVurderingDbRecord>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll() {
        TODO("Not yet implemented")
    }
}
