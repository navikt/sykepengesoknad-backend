package no.nav.helse.flex.fakes

import no.nav.helse.flex.medlemskap.MedlemskapVurderingDbRecord
import no.nav.helse.flex.medlemskap.MedlemskapVurderingRepository
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
@Profile("fakes")
@Primary
class MedlemskapsrepositoryFake :
    InMemoryCrudRepository<MedlemskapVurderingDbRecord, String>({ it.sykepengesoknadId }),
    MedlemskapVurderingRepository {
    override fun findBySykepengesoknadIdAndFomAndTom(
        sykepengesoknadId: String,
        fom: LocalDate,
        tom: LocalDate,
    ): MedlemskapVurderingDbRecord? {
        // Eksempel: Søk i `store` (eller bare finn alt) og filtrer
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
}
