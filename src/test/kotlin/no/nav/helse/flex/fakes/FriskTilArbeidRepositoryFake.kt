package no.nav.helse.flex.fakes

import no.nav.helse.flex.frisktilarbeid.BehandletStatus
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidRepository
import no.nav.helse.flex.frisktilarbeid.FriskTilArbeidVedtakDbRecord
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
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
    override fun finnVedtakSomSkalBehandles(antallVedtak: Int): List<FriskTilArbeidVedtakDbRecord> =
        findAll()
            .filter { it.behandletStatus == BehandletStatus.NY }
            .sortedBy { it.opprettet }
            .take(antallVedtak)

    override fun deleteByFnr(fnr: String): Long {
        val toDelete = findAll().filter { it.fnr == fnr }
        toDelete.forEach { delete(it) }
        return toDelete.size.toLong()
    }

    override fun findByFnrIn(fnrs: List<String>): List<FriskTilArbeidVedtakDbRecord> = findAll().filter { it.fnr in fnrs }

    override fun oppdaterStatusTilNyForId(id: String): Int {
        TODO("Not yet implemented")
    }
}
