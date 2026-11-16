package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.fakes.SoknadKafkaProducerFake
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

class ArbeidssokerregisterStartStoppServiceTest : FakesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    @Autowired
    private lateinit var sykepengesoknadRepository: SykepengesoknadRepository

    @Autowired
    private lateinit var service: ArbeidssokerregisterStartStoppService

    private val fnr = "11111111111"

    @BeforeEach
    @AfterEach
    fun nullstillTestdata() {
        friskTilArbeidRepository.deleteAll()
        sykepengesoknadRepository.deleteAll()
        SoknadKafkaProducerFake.records.clear()
    }

    @ParameterizedTest(name = "STOPP sletter søknad med status {0} etter stoppdato")
    @EnumSource(value = Soknadstatus::class, names = ["NY", "FREMTIDIG"])
    fun `STOPP sletter bare aktuelle søknader etter stoppdato og setter avsluttetTidspunkt`(status: Soknadstatus) {
        val stoppTidspunkt = Instant.parse("2025-02-10T12:00:00Z")
        val vedtak = fattetVedtak(fnr, "vedtak-1")
        val slettbarSoknad =
            soknadForVedtak(
                vedtakId = vedtak.id!!,
                status = status,
                fom = LocalDate.of(2025, 2, 11),
            )
        val ikkeSlettbarSammeDag =
            soknadForVedtak(
                vedtakId = vedtak.id,
                status = Soknadstatus.NY,
                fom = LocalDate.of(2025, 2, 10),
            )
        val annenSoknad =
            soknadForVedtak(
                vedtakId = "annen-vedtak-id",
                status = Soknadstatus.FREMTIDIG,
                fom = LocalDate.of(2025, 2, 11),
            )

        val soknaderFor = vedtakHarSoknader(vedtak, listOf(slettbarSoknad, ikkeSlettbarSammeDag, annenSoknad))

        service.prosseserStartStoppMelding(stoppMelding(vedtak.id, stoppTidspunkt))

        friskTilArbeidRepository.findById(vedtak.id).orElseThrow() shouldBe vedtak.copy(avsluttetTidspunkt = stoppTidspunkt)
        sykepengesoknadRepository.findAll().toList() shouldContainExactlyInAnyOrder
            soknaderFor.filter { it.sykepengesoknadUuid != slettbarSoknad.id }
        forventSlettetSoknadPublisert(slettbarSoknad.id, vedtak.id)
    }

    @Test
    fun `STOPP på allerede avsluttet vedtak bevarer opprinnelig tidspunkt og ignorerer slettede søknader`() {
        val opprinneligAvsluttet = Instant.parse("2025-02-10T12:00:00Z")
        val nyStopp = Instant.parse("2025-02-12T12:00:00Z")
        val vedtak = fattetVedtak(fnr, "vedtak-1").copy(avsluttetTidspunkt = opprinneligAvsluttet)
        val alleredeSlettet =
            soknadForVedtak(
                vedtakId = vedtak.id!!,
                status = Soknadstatus.SLETTET,
                fom = LocalDate.of(2025, 2, 13),
            )

        val soknaderFor = vedtakHarSoknader(vedtak, listOf(alleredeSlettet))

        service.prosseserStartStoppMelding(stoppMelding(vedtak.id, nyStopp))

        friskTilArbeidRepository.findById(vedtak.id).orElseThrow() shouldBe vedtak
        sykepengesoknadRepository.findAll().toList() shouldContainExactlyInAnyOrder soknaderFor
        SoknadKafkaProducerFake.records.shouldBeEmpty()
    }

    @Test
    fun `Kaster feil når vedtak ikke finnes`() {
        val exception =
            assertThrows<IllegalStateException> {
                service.prosseserStartStoppMelding(stoppMelding("ukjent", FriskTilArbeidTestTid.tidspunkt))
            }

        exception.message shouldBe "Fant ikke vedtak for STOPP-melding med vedtaksperiode: ukjent."
        friskTilArbeidRepository.findAll().shouldBeEmpty()
        sykepengesoknadRepository.findAll().shouldBeEmpty()
        SoknadKafkaProducerFake.records.shouldBeEmpty()
    }

    @Test
    fun `Kaster feil når vedtak ignorerer arbeidssøkerregisteret`() {
        val vedtakId = "vedtak-1"
        val vedtak = fattetVedtak(fnr, vedtakId).copy(ignorerArbeidssokerregister = true)
        friskTilArbeidRepository.save(vedtak)

        val exception =
            assertThrows<IllegalStateException> {
                service.prosseserStartStoppMelding(stoppMelding(vedtakId, FriskTilArbeidTestTid.tidspunkt))
            }

        exception.message shouldBe
            "Kan ikke behandle STOPP-melding for vedtaksperiode: ${vedtak.id} " +
            "siden vedtaket er satt til å ignorere arbeidssøkerregisteret."
        friskTilArbeidRepository.findById(vedtakId).orElseThrow() shouldBe vedtak
        sykepengesoknadRepository.findAll().shouldBeEmpty()
        SoknadKafkaProducerFake.records.shouldBeEmpty()
    }

    @ParameterizedTest(name = "STOPP bevarer søknad med status {0}")
    @EnumSource(value = Soknadstatus::class, names = ["NY", "FREMTIDIG"], mode = EnumSource.Mode.EXCLUDE)
    fun `STOPP bevarer søknader som ikke er NY eller FREMTIDIG`(status: Soknadstatus) {
        val stopp = Instant.parse("2025-02-10T12:00:00Z")
        val vedtak = fattetVedtak(fnr, "vedtak-1").copy(avsluttetTidspunkt = stopp)
        val soknad =
            soknadForVedtak(
                vedtakId = vedtak.id!!,
                status = status,
                fom = LocalDate.of(2025, 2, 11),
            )
        val soknaderFor = vedtakHarSoknader(vedtak, listOf(soknad))

        service.prosseserStartStoppMelding(stoppMelding(vedtak.id, stopp))

        friskTilArbeidRepository.findById(vedtak.id).orElseThrow() shouldBe vedtak
        sykepengesoknadRepository.findAll().toList() shouldContainExactlyInAnyOrder soknaderFor
        SoknadKafkaProducerFake.records.shouldBeEmpty()
    }

    @ParameterizedTest(name = "STOPP ved {0} bruker norsk dato {1}")
    @CsvSource(
        "2025-02-10T23:30:00Z, 2025-02-11",
        "2025-06-10T22:30:00Z, 2025-06-11",
    )
    fun `STOPP bruker norsk dato ved vintertid og sommertid`(
        tidspunkt: String,
        norskDato: String,
    ) {
        val stopp = Instant.parse(tidspunkt)
        val dato = LocalDate.parse(norskDato)
        val vedtak = fattetVedtak(fnr, "vedtak-1")
        val sammeDag = soknadForVedtak(vedtak.id!!, status = Soknadstatus.NY, fom = dato)
        val nesteDag = soknadForVedtak(vedtak.id, status = Soknadstatus.FREMTIDIG, fom = dato.plusDays(1))
        val soknaderFor = vedtakHarSoknader(vedtak, listOf(sammeDag, nesteDag))

        service.prosseserStartStoppMelding(stoppMelding(vedtak.id, stopp))

        sykepengesoknadRepository.findAll().toList() shouldContainExactlyInAnyOrder
            soknaderFor.filter { it.sykepengesoknadUuid != nesteDag.id }
        forventSlettetSoknadPublisert(nesteDag.id, vedtak.id)
        friskTilArbeidRepository.findById(vedtak.id).orElseThrow() shouldBe vedtak.copy(avsluttetTidspunkt = stopp)
    }

    private fun fattetVedtak(
        fnr: String,
        vedtakId: String,
    ) = lagFriskTilArbeidVedtakDbRecord(lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET)).copy(id = vedtakId)

    private fun soknadForVedtak(
        vedtakId: String,
        status: Soknadstatus,
        fom: LocalDate,
    ): Sykepengesoknad =
        lagFriskTilArbeidSoknad(
            fnr = fnr,
            friskTilArbeidVedtakId = vedtakId,
            soknadStatus = status,
            fom = fom,
            tom = fom.plusDays(13),
        )

    private fun stoppMelding(
        vedtakId: String,
        tidspunkt: Instant,
    ) = ArbeidssokerperiodeStartStoppMelding(
        operation = StartStopp.STOPP,
        vedtaksperiodeId = vedtakId,
        fnr = fnr,
        tidspunkt = tidspunkt,
    )

    private fun vedtakHarSoknader(
        vedtak: FriskTilArbeidVedtakDbRecord,
        soknader: List<Sykepengesoknad>,
    ): List<SykepengesoknadDbRecord> {
        friskTilArbeidRepository.save(vedtak)
        soknader.forEach { sykepengesoknadDAO.lagreSykepengesoknad(it) }
        return sykepengesoknadRepository.findAll().toList()
    }

    private fun forventSlettetSoknadPublisert(
        soknadId: String,
        vedtakId: String,
    ) {
        val hendelse =
            SoknadKafkaProducerFake.records
                .map { it.value() }
                .shouldHaveSize(1)
                .single()
        hendelse.id shouldBe soknadId
        hendelse.status shouldBe SoknadsstatusDTO.SLETTET
        hendelse.friskTilArbeidVedtakId shouldBe vedtakId
    }
}
