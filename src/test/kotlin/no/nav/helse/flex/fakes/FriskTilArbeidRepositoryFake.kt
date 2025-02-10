package no.nav.helse.flex.fakes

import no.nav.helse.flex.frisktilarbeid.BehandletStatus
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidRepository
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidVedtakDbRecord
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
class FriskTilArbeidRepositoryFake :
    InMemoryCrudRepository<FriskTilArbeidVedtakDbRecord, String>(
        getId = { it.id },
        copyWithId = { record, newId ->
            record.copy(id = newId)
        },
        generateId = { UUID.randomUUID().toString() },
    ),
    FriskTilArbeidRepository {
    override fun finnVedtakSomSkalBehandles(antallVedtak: Int): List<FriskTilArbeidVedtakDbRecord> {
        return findAll().filter { it.behandletStatus == BehandletStatus.NY }.sortedBy { it.opprettet }
            .take(antallVedtak)
    }

    override fun deleteByFnr(fnr: String): Long {
        val toDelete = findAll().filter { it.fnr == fnr }
        toDelete.forEach { delete(it) }
        return toDelete.size.toLong()
    }

    override fun findByFnr(fnr: String): List<FriskTilArbeidVedtakDbRecord> {
        return findAll().filter { it.fnr == fnr }
    }

}
