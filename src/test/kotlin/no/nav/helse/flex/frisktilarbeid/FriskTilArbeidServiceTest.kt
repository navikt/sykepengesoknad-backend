package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.fakes.InMemoryCrudRepository
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
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
    private val key = fnr.asProducerRecordKey()

    @Test
    fun `Lagrer vedtak med status FATTET`() {
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
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = key,
                friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FERDIG_BEHANDLET),
            ),
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 0
    }

    @Test
    fun `Lagrer to perioder som ikke overlapper`() {
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = key,
                friskTilArbeidVedtakStatus =
                    lagFriskTilArbeidVedtakStatus(
                        fnr,
                        Status.FATTET,
                        Vedtaksperiode(
                            periodeStart = LocalDate.of(2024, 1, 1),
                            periodeSlutt = LocalDate.of(2024, 1, 7),
                        ),
                    ),
            ),
        )
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = key,
                friskTilArbeidVedtakStatus =
                    lagFriskTilArbeidVedtakStatus(
                        fnr,
                        Status.FATTET,
                        Vedtaksperiode(
                            periodeStart = LocalDate.of(2024, 1, 8),
                            periodeSlutt = LocalDate.of(2024, 1, 15),
                        ),
                    ),
            ),
        )
        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 2
    }

    @Test
    fun `Lagrer to overlappende perioder for to forskjellige brukere`() {
        lagFriskTilArbeidVedtakStatus(
            Vedtaksperiode(
                periodeStart = LocalDate.of(2024, 1, 1),
                periodeSlutt = LocalDate.of(2024, 1, 7),
            ),
        )

        lagFriskTilArbeidVedtakStatus(
            Vedtaksperiode(
                periodeStart = LocalDate.of(2024, 1, 5),
                periodeSlutt = LocalDate.of(2024, 1, 13),
            ),
            personident = "22222222222",
        )

        fakeFriskTilArbeidRepository.findAll().toList() shouldHaveSize 2
    }

    @Test
    fun `Feiler ved lagring av to vedtak med samme fom og tom`() {
        lagFriskTilArbeidVedtakStatus(
            Vedtaksperiode(
                periodeStart = LocalDate.of(2024, 1, 1),
                periodeSlutt = LocalDate.of(2024, 1, 7),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Vedtaksperiode(
                    periodeStart = LocalDate.of(2024, 1, 1),
                    periodeSlutt = LocalDate.of(2024, 1, 7),
                ),
            )
        }
    }

    @Test
    fun `Feiler ved lagring av nytt vedtak med fom før eksisterende vedtaks tom`() {
        lagFriskTilArbeidVedtakStatus(
            Vedtaksperiode(
                periodeStart = LocalDate.of(2024, 1, 1),
                periodeSlutt = LocalDate.of(2024, 1, 7),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Vedtaksperiode(
                    periodeStart = LocalDate.of(2024, 1, 5),
                    periodeSlutt = LocalDate.of(2024, 1, 13),
                ),
            )
        }
    }

    @Test
    fun `Feiler ved lagring av nytt vedtak med tom etter eksisterende vedtaks fom`() {
        lagFriskTilArbeidVedtakStatus(
            Vedtaksperiode(
                periodeStart = LocalDate.of(2024, 1, 5),
                periodeSlutt = LocalDate.of(2024, 1, 13),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Vedtaksperiode(
                    periodeStart = LocalDate.of(2024, 1, 1),
                    periodeSlutt = LocalDate.of(2024, 1, 7),
                ),
            )
        }
    }

    @Test
    fun `Feiler ved lagring av nytt vedtak med fom lik eksisterende vedtaks tom`() {
        lagFriskTilArbeidVedtakStatus(
            Vedtaksperiode(
                periodeStart = LocalDate.of(2024, 1, 1),
                periodeSlutt = LocalDate.of(2024, 1, 7),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Vedtaksperiode(
                    periodeStart = LocalDate.of(2024, 1, 7),
                    periodeSlutt = LocalDate.of(2024, 1, 14),
                ),
            )
        }
    }

    @Test
    fun `Feiler ved lagring av nytt vedtak med tom lik eksisterende vedtaks fom`() {
        lagFriskTilArbeidVedtakStatus(
            Vedtaksperiode(
                periodeStart = LocalDate.of(2024, 1, 7),
                periodeSlutt = LocalDate.of(2024, 1, 14),
            ),
        )

        assertThrows<FriskTilArbeidVedtakStatusException> {
            lagFriskTilArbeidVedtakStatus(
                Vedtaksperiode(
                    periodeStart = LocalDate.of(2024, 1, 1),
                    periodeSlutt = LocalDate.of(2024, 1, 7),
                ),
            )
        }
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

    private fun lagFriskTilArbeidVedtakStatus(
        vedtaksperiode: Vedtaksperiode,
        personident: String = fnr,
    ) {
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = personident.asProducerRecordKey(),
                friskTilArbeidVedtakStatus =
                    lagFriskTilArbeidVedtakStatus(
                        personident,
                        Status.FATTET,
                        vedtaksperiode,
                    ),
            ),
        )
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

        override fun findByFnr(fnr: String): List<FriskTilArbeidVedtakDbRecord> = findAll().filter { it.fnr == fnr }
    }
}
