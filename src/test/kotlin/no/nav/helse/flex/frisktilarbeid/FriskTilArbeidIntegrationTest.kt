package no.nav.helse.flex.frisktilarbeid

import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.`should be within seconds of`
import no.nav.helse.flex.subscribeHvisIkkeSubscribed
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadstypeDTO
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FriskTilArbeidIntegrationTest : FellesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidKafkaConsumer: Consumer<String, String>

    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @Autowired
    private lateinit var friskTilArbeidService: FriskTilArbeidService

    @BeforeAll
    fun subscribeToTopic() {
        friskTilArbeidKafkaConsumer.subscribeHvisIkkeSubscribed(FRISKTILARBEID_TOPIC)
    }

    @BeforeAll
    @AfterAll
    fun slettDatabase() {
        sykepengesoknadRepository.deleteAll()
        friskTilArbeidRepository.deleteAll()
    }

    @Test
    @Order(1)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        // To 14-dagersperioder og én 7-dagersperiode.
        val vedtaksperiode =
            Periode(
                fom = LocalDate.of(2025, 2, 3),
                tom = LocalDate.of(2025, 3, 9),
            )
        val fnr = "11111111111"
        val key = fnr.asProducerRecordKey()
        val friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET, vedtaksperiode)

        kafkaProducer
            .send(
                ProducerRecord(
                    FRISKTILARBEID_TOPIC,
                    key,
                    friskTilArbeidVedtakStatus.serialisertTilString(),
                ),
            ).get()

        friskTilArbeidKafkaConsumer.ventPåRecords(1, Duration.ofSeconds(1)).first().also {
            it.value() shouldBe friskTilArbeidVedtakStatus.serialisertTilString()
        }

        val vedtakSomSkalBehandles =
            await().atMost(1, TimeUnit.SECONDS).until(
                { friskTilArbeidRepository.finnVedtakSomSkalBehandles(1) },
                { it.size == 1 },
            )

        vedtakSomSkalBehandles.first().also {
            it.fnr shouldBe fnr
            it.behandletStatus shouldBe BehandletStatus.NY
            it.behandletTidspunkt shouldBe null
            it.fom shouldBe vedtaksperiode.fom
            it.tom shouldBe vedtaksperiode.tom
            it.ignorerArbeidssokerregister shouldBe null
        }
    }

    @Test
    @Order(2)
    fun `Oppretter søknad fra med status FREMTIDIG`() {
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size shouldBe 0

        val friskTilArbeidDbRecord =
            friskTilArbeidRepository.findAll().first().also {
                it.behandletStatus shouldBe BehandletStatus.BEHANDLET
                it.behandletTidspunkt!! `should be within seconds of` (1 to Instant.now())
            }

        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!).also {
            it.size shouldBe 3
            it.forEach {
                it.status shouldBe Soknadstatus.FREMTIDIG
                it.friskTilArbeidVedtakId shouldBe friskTilArbeidDbRecord.id
            }
        }

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3, Duration.ofSeconds(1)).tilSoknader().also { soknader ->
            soknader.size shouldBe 3
            soknader.forEach {
                it.fnr shouldBe "11111111111"
                it.status shouldBe SoknadsstatusDTO.FREMTIDIG
                it.type shouldBe SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING

                it.friskTilArbeidVedtakId shouldBe friskTilArbeidDbRecord.id
                it.friskTilArbeidVedtakPeriode shouldBe
                    Periode(
                        fom = friskTilArbeidDbRecord.fom,
                        tom = friskTilArbeidDbRecord.tom,
                    ).serialisertTilString()
                it.ignorerArbeidssokerregister shouldBe null
            }
        }
    }

    @Test
    @Order(3)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET som ikke skal sjekkes mot Arbeidssøkerregisteret`() {
        val fnr = "22222222222"
        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = fnr.asProducerRecordKey(),
                friskTilArbeidVedtakStatus =
                    lagFriskTilArbeidVedtakStatus(
                        fnr,
                        Status.FATTET,
                    ).copy(statusAt = OffsetDateTime.of(LocalDateTime.of(2025, 3, 9, 23, 1, 0), ZoneOffset.UTC)),
                ignorerArbeidssokerregister = true,
            ),
        )
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size shouldBe 0

        friskTilArbeidRepository.findAll().first { it -> it.fnr == fnr }.also {
            it.behandletStatus shouldBe BehandletStatus.BEHANDLET
        }

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1, Duration.ofSeconds(1)).tilSoknader().also { soknader ->
            soknader.size shouldBe 1
            soknader.forEach {
                it.fnr shouldBe fnr
                it.status shouldBe SoknadsstatusDTO.FREMTIDIG
                it.type shouldBe SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING
                it.ignorerArbeidssokerregister shouldBe true
            }
        }
    }
}
