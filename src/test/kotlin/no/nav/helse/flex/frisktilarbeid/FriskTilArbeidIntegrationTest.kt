package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.repository.SykepengesoknadDbRecord
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant
import java.time.LocalDate

class FriskTilArbeidIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @Autowired
    private lateinit var friskTilArbeidService: FriskTilArbeidService

    private val fnr = "11111111111"
    private val vedtaksperiode = Periode(LocalDate.of(2025, 2, 3), LocalDate.of(2025, 3, 9))

    @BeforeEach
    fun slettDatabase() {
        databaseReset.resetDatabase()
    }

    @Test
    fun `Mottar vedtak med status FATTET fra Kafka og lagrer det for behandling`() {
        val vedtak = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET, vedtaksperiode)
        val key = fnr.asProducerRecordKey()

        kafkaProducer.send(ProducerRecord(FRISKTILARBEID_TOPIC, key, vedtak.serialisertTilString())).get()

        forventVedtakLagretForBehandling(vedtak, key)
    }

    @Test
    fun `Behandler vedtak og oppretter tre fremtidige søknader med Kafka-hendelser`() {
        val vedtak = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET, vedtaksperiode)
        lagreVedtakForBehandling(vedtak)
        val forBehandling = Instant.now()

        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)

        val lagret = friskTilArbeidRepository.findByFnrIn(listOf(fnr)).single()
        lagret.behandletStatus shouldBe BehandletStatus.BEHANDLET
        lagret.behandletTidspunkt.shouldNotBeNull().also {
            it.isBefore(forBehandling) shouldBe false
            it.isAfter(Instant.now()) shouldBe false
        }
        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).shouldBeEmpty()

        val vedtakId = lagret.id.shouldNotBeNull()
        val soknader =
            forventFremtidigeSoknaderFraVedtak(
                vedtakId,
                forventedePerioder =
                    listOf(
                        LocalDate.of(2025, 2, 3) to LocalDate.of(2025, 2, 16),
                        LocalDate.of(2025, 2, 17) to LocalDate.of(2025, 3, 2),
                        LocalDate.of(2025, 3, 3) to LocalDate.of(2025, 3, 9),
                    ),
            )

        forventPubliserteFremtidigeSoknader(
            vedtakId,
            soknadIder = soknader.map { it.sykepengesoknadUuid },
            ignorerArbeidssokerregister = null,
        ).forEach { it.friskTilArbeidVedtakPeriode shouldBe vedtaksperiode.serialisertTilString() }
    }

    @Test
    fun `Behandler vedtak som skal ignorere arbeidssøkerregisteret og publiserer flagget`() {
        val vedtak =
            lagFriskTilArbeidVedtakStatus(
                fnr,
                Status.FATTET,
                Periode(LocalDate.of(2025, 3, 10), LocalDate.of(2025, 3, 23)),
            )
        lagreVedtakForBehandling(vedtak, ignorerArbeidssokerregister = true)

        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).shouldBeEmpty()
        val lagret = friskTilArbeidRepository.findByFnrIn(listOf(fnr)).single()
        lagret.behandletStatus shouldBe BehandletStatus.BEHANDLET
        lagret.ignorerArbeidssokerregister shouldBe true
        val vedtakId = lagret.id.shouldNotBeNull()
        val soknad = sykepengesoknadRepository.findByFriskTilArbeidVedtakId(vedtakId).shouldHaveSize(1).single()
        soknad.status shouldBe Soknadstatus.FREMTIDIG

        forventPubliserteFremtidigeSoknader(
            vedtakId,
            soknadIder = listOf(soknad.sykepengesoknadUuid),
            ignorerArbeidssokerregister = true,
        )
    }

    private fun lagreVedtakForBehandling(
        vedtak: FriskTilArbeidVedtakStatus,
        ignorerArbeidssokerregister: Boolean? = null,
    ) {
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(fnr.asProducerRecordKey(), vedtak, ignorerArbeidssokerregister),
        )
    }

    private fun forventVedtakLagretForBehandling(
        vedtak: FriskTilArbeidVedtakStatus,
        key: String,
    ) {
        ventPaFriskTilArbeid("Kafka-vedtak er lagret og klart for behandling").untilAsserted {
            val lagret = friskTilArbeidRepository.findByFnrIn(listOf(fnr)).shouldHaveSize(1).single()
            lagret.key shouldBe key
            lagret.fnr shouldBe fnr
            lagret.behandletStatus shouldBe BehandletStatus.NY
            lagret.behandletTidspunkt shouldBe null
            lagret.fom shouldBe vedtak.fom
            lagret.tom shouldBe vedtak.tom
            lagret.ignorerArbeidssokerregister shouldBe null
            lagret.vedtak
                .shouldNotBeNull()
                .value
                .shouldNotBeNull()
                .tilFriskTilArbeidVedtakStatus() shouldBe vedtak
            friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).map { it.id } shouldBe listOf(lagret.id)
        }
    }

    private fun forventFremtidigeSoknaderFraVedtak(
        vedtakId: String,
        forventedePerioder: List<Pair<LocalDate, LocalDate>>,
    ): List<SykepengesoknadDbRecord> {
        val soknader = sykepengesoknadRepository.findByFriskTilArbeidVedtakId(vedtakId).sortedBy { it.fom }
        soknader.map { it.fom to it.tom } shouldBe forventedePerioder
        soknader.forEach { it.status shouldBe Soknadstatus.FREMTIDIG }
        return soknader
    }

    private fun forventPubliserteFremtidigeSoknader(
        vedtakId: String,
        soknadIder: List<String>,
        ignorerArbeidssokerregister: Boolean?,
    ): List<SykepengesoknadDTO> {
        val hendelser = sykepengesoknadKafkaConsumer.ventPåRecords(antall = soknadIder.size).tilSoknader()
        hendelser.map { it.id } shouldContainExactlyInAnyOrder soknadIder
        hendelser.forEach {
            it.fnr shouldBe fnr
            it.status shouldBe SoknadsstatusDTO.FREMTIDIG
            it.type shouldBe SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING
            it.friskTilArbeidVedtakId shouldBe vedtakId
            it.ignorerArbeidssokerregister shouldBe ignorerArbeidssokerregister
        }
        return hendelser
    }
}
