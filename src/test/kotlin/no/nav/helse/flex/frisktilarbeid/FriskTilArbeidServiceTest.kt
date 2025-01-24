package no.nav.helse.flex.frisktilarbeid

import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import java.util.UUID


class FriskTilArbeidServiceTest {

    private val friskTilArbeidRepository = FakeMessageRepository()
    private val friskTilArbeidService = FriskTilArbeidService(friskTilArbeidRepository)

    private val fnr = "11111111111"

    @BeforeEach
    fun slettFraDatabase() {
        friskTilArbeidRepository.deleteAll()
    }

    @Test
    fun `Lagrer vedtak med status FATTET`() {
        val key = fnr.asProducerRecordKey()

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusMelding(
                key = key,
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET),
            )
        )

        friskTilArbeidRepository.findAll().toList() shouldHaveSize 1
    }

    @Test
    fun `Lagrer ikke vedtak med status FERDIG_BEHANDLET`() {
        val key = fnr.asProducerRecordKey()

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusMelding(
                key = key,
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FERDIG_BEHANDLET),
            )
        )

        friskTilArbeidRepository.findAll().toList() shouldHaveSize 0
    }
}


@Suppress("UNCHECKED_CAST")
class FakeMessageRepository : FriskTilArbeidRepository {

    private val dbRecords = mutableMapOf<String, FriskTilArbeidDbRecord>()

    override fun <S : FriskTilArbeidDbRecord?> save(friskTilArbeidDbRecord: S & Any): S & Any {
        val id = UUID.randomUUID().toString()
        val lagretDbRecord = friskTilArbeidDbRecord.copy(id = id)
        dbRecords[id] = lagretDbRecord
        return lagretDbRecord as (S & Any)
    }

    override fun deleteAll() {
        dbRecords.clear()
    }

    override fun findAll(): Iterable<FriskTilArbeidDbRecord?> {
        return dbRecords.values
    }

    override fun <S : FriskTilArbeidDbRecord?> saveAll(entities: Iterable<S?>): Iterable<S?> {
        TODO("Not yet implemented")
    }

    override fun findById(id: String): Optional<FriskTilArbeidDbRecord?> {
        TODO("Not yet implemented")
    }

    override fun existsById(id: String): Boolean {
        TODO("Not yet implemented")
    }

    override fun findAllById(ids: Iterable<String?>): Iterable<FriskTilArbeidDbRecord?> {
        TODO("Not yet implemented")
    }

    override fun count(): Long {
        TODO("Not yet implemented")
    }

    override fun deleteById(id: String) {
        TODO("Not yet implemented")
    }

    override fun delete(entity: FriskTilArbeidDbRecord) {
        TODO("Not yet implemented")
    }

    override fun deleteAllById(ids: Iterable<String?>) {
        TODO("Not yet implemented")
    }

    override fun deleteAll(entities: Iterable<FriskTilArbeidDbRecord?>) {
        TODO("Not yet implemented")
    }
}