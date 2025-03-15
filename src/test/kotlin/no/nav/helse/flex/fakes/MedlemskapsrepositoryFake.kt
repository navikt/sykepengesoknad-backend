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
class MedlemskapsrepositoryFake :
    InMemoryCrudRepository<MedlemskapVurderingDbRecord, String>(
        getId = { it.id },
        copyWithId = { record, newId ->
            record.copy(id = newId)
        },
        generateId = { UUID.randomUUID().toString() },
    ),
    MedlemskapVurderingRepository {
    override fun findBySykepengesoknadIdAndFomAndTom(
        sykepengesoknadId: String,
        fom: LocalDate,
        tom: LocalDate,
    ): MedlemskapVurderingDbRecord? {
        return findAll()
            .firstOrNull {
                it.sykepengesoknadId == sykepengesoknadId &&
                    it.fom == fom &&
                    it.tom == tom
            }
    }

    override fun deleteBySykepengesoknadId(sykepengesoknadId: String): Long {
        val toDelete = findAll().filter { it.sykepengesoknadId == sykepengesoknadId }
        toDelete.forEach { delete(it) }
        return toDelete.size.toLong()
    }

    override fun deleteByFnr(fnr: String): Long {
        val toDelete = findAll().filter { it.fnr == fnr }
        toDelete.forEach { delete(it) }
        return toDelete.size.toLong()
    }

    override fun findAllBySykepengesoknadId(ids: List<String>): List<MedlemskapVurderingDbRecord> {
        return findAll().filter { it.id in ids }
    }
}
