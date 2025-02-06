package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.fakes.InMemoryCrudRepository
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import java.time.LocalDate
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
        val all = fakeFriskTilArbeidRepository.findAll().toList()

        val en = all.find { it.fnr == "11111111111" }
        val to = all.find { it.fnr == "22222222222" }

        fakeFriskTilArbeidRepository.findAll().toList().also {
            it.find { it.fnr == "11111111111" }?.behandletStatus `should be equal to` BehandletStatus.BEHANDLET
            it.find { it.fnr == "22222222222" }?.behandletStatus `should be equal to` BehandletStatus.NY
        }
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
        return FriskTilArbeidRepositoryFake()
    }

    class FakeFriskTilArbeidSoknadService(
        private val friskTilArbeidRepository: FriskTilArbeidRepository,
    ) : FriskTilArbeidSoknadService(friskTilArbeidRepository, null, null) {
        override fun opprettSoknader(
            friskTilArbeidDbRecord: FriskTilArbeidVedtakDbRecord,
            periodGenerator: (LocalDate, LocalDate, Long) -> List<Pair<LocalDate, LocalDate>>,
        ) {
            friskTilArbeidRepository.save(friskTilArbeidDbRecord.copy(behandletStatus = BehandletStatus.BEHANDLET))
        }
    }

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
            return findAll().filter { it.behandletStatus == BehandletStatus.NY }
                .sortedBy { it.opprettet }
                .take(antallVedtak)
        }

        override fun deleteByFnr(fnr: String): Long {
            val toDelete = findAll().filter { it.fnr == fnr }
            toDelete.forEach { delete(it) }
            return toDelete.size.toLong()
        }
    }
}
