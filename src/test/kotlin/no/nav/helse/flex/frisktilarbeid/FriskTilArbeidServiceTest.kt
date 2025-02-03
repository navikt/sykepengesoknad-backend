package no.nav.helse.flex.frisktilarbeid

import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.util.*

@SpringBootTest(classes = [FriskTilArbeidServiceTestConfiguration::class])
class FriskTilArbeidServiceTest {
    @Autowired
    private lateinit var fakeFriskTilArbeidSoknadService: FriskTilArbeidSoknadService

    @Autowired
    private lateinit var fakeFriskTilArbeidRepository: FriskTilArbeidRepository

    lateinit var friskTilArbeidService: FriskTilArbeidService

    @BeforeEach
    fun setup() {
        fakeFriskTilArbeidRepository.deleteAll()
        friskTilArbeidService = FriskTilArbeidService(fakeFriskTilArbeidRepository, fakeFriskTilArbeidSoknadService)
    }

    private val fnr = "11111111111"

    @BeforeEach
    fun slettFraDatabase() {
        fakeFriskTilArbeidRepository.deleteAll()
    }

    @Test
    fun `Lagrer vedtak med status FATTET`() {
        val key = fnr.asProducerRecordKey()

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = key,
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET),
            ),
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 1
    }

    @Test
    fun `Lagrer ikke vedtak med status FERDIG_BEHANDLET`() {
        val key = fnr.asProducerRecordKey()

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = key,
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FERDIG_BEHANDLET),
            ),
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 0
    }

    @Test
    fun `Behandler ett av to vedtak`() {
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = "11111111111".asProducerRecordKey(),
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus("11111111111", Status.FATTET),
            ),
        )

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = "22222222222".asProducerRecordKey(),
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus("22222222222", Status.FATTET),
            ),
        )

        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)

        fakeFriskTilArbeidRepository.findAll().toList().also {
            it.find { it.fnr == "11111111111" }?.behandletStatus `should be equal to` BehandletStatus.BEHANDLET
            it.find { it.fnr == "22222222222" }?.behandletStatus `should be equal to` BehandletStatus.NY
        }
    }
}

// Må være top-level for å kunne brukes i @TestConfiguration siden Spring sliter med å generere CGLIB
// subclass av anonym klasse (object).
class FakeFriskTilArbeidSoknadService(
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
) : FriskTilArbeidSoknadService(friskTilArbeidRepository, null, null) {
    override fun opprettSoknad(friskTilArbeidDbRecord: FriskTilArbeidVedtakDbRecord) {
        friskTilArbeidRepository.save(friskTilArbeidDbRecord.copy(behandletStatus = BehandletStatus.BEHANDLET))
    }
}

@Suppress("UNCHECKED_CAST")
@TestConfiguration
class FriskTilArbeidTestConfig {
    @Bean
    @Qualifier("fakeFriskTilArbeidSoknadService")
    fun fakeFriskTilArbeidSoknadService(friskTilArbeidRepository: FriskTilArbeidRepository): FriskTilArbeidSoknadService {
        return FakeFriskTilArbeidSoknadService(friskTilArbeidRepository)
    }

    @Bean
    @Qualifier("fakeFriskTilArbeidRepository")
    fun fakeFriskTilArbeidRepository(): FriskTilArbeidRepository {
        return object : FriskTilArbeidRepository {
            private val dbRecords = mutableMapOf<String, FriskTilArbeidVedtakDbRecord>()

            override fun <S : FriskTilArbeidVedtakDbRecord?> save(friskTilArbeidDbRecord: S & Any): S & Any {
                // Bruker eksisterende id for UPDATE eller genererer for INSERT.
                val id = friskTilArbeidDbRecord.id ?: UUID.randomUUID().toString()
                val lagretDbRecord = friskTilArbeidDbRecord.copy(id = id)
                dbRecords[id] = lagretDbRecord
                return lagretDbRecord as (S & Any)
            }

            override fun deleteAll() {
                dbRecords.clear()
            }

            override fun findAll(): Iterable<FriskTilArbeidVedtakDbRecord?> {
                return dbRecords.values
            }

            override fun finnVedtakSomSkalBehandles(antallVedtak: Int): List<FriskTilArbeidVedtakDbRecord> {
                return dbRecords.values.filter { it.behandletStatus == BehandletStatus.NY }.take(antallVedtak)
            }

            override fun <S : FriskTilArbeidVedtakDbRecord?> saveAll(entities: Iterable<S?>): Iterable<S?> {
                TODO("Not yet implemented")
            }

            override fun findById(id: String): Optional<FriskTilArbeidVedtakDbRecord> {
                return Optional.ofNullable(dbRecords[id])
            }

            override fun existsById(id: String): Boolean {
                TODO("Not yet implemented")
            }

            override fun findAllById(ids: Iterable<String?>): Iterable<FriskTilArbeidVedtakDbRecord?> {
                TODO("Not yet implemented")
            }

            override fun count(): Long {
                TODO("Not yet implemented")
            }

            override fun deleteById(id: String) {
                TODO("Not yet implemented")
            }

            override fun delete(entity: FriskTilArbeidVedtakDbRecord) {
                TODO("Not yet implemented")
            }

            override fun deleteAllById(ids: Iterable<String?>) {
                TODO("Not yet implemented")
            }

            override fun deleteAll(entities: Iterable<FriskTilArbeidVedtakDbRecord?>) {
                TODO("Not yet implemented")
            }
        }
    }
}
