package no.nav.helse.flex.frisktilarbeid

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
import org.amshove.kluent.`should be equal to`
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
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class FriskTilArbeidIntegrationTest() : FellesTestOppsett() {
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

    private val fnr = "11111111111"

    @Test
    @Order(1)
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        // To 14-dagersperioder og én 7-dagersperiode.
        val vedtaksperiode =
            Periode(
                fom = LocalDate.of(2025, 2, 3),
                tom = LocalDate.of(2025, 3, 9),
            )
        val fnr = fnr
        val key = fnr.asProducerRecordKey()
        val friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET, vedtaksperiode)

        kafkaProducer.send(
            ProducerRecord(
                FRISKTILARBEID_TOPIC,
                key,
                friskTilArbeidVedtakStatus.serialisertTilString(),
            ),
        ).get()

        friskTilArbeidKafkaConsumer.ventPåRecords(1, Duration.ofSeconds(1)).first().also {
            it.value() `should be equal to` friskTilArbeidVedtakStatus.serialisertTilString()
        }

        val vedtakSomSkalBehandles =
            await().atMost(1, TimeUnit.SECONDS).until(
                { friskTilArbeidRepository.finnVedtakSomSkalBehandles(1) },
                { it.size == 1 },
            )

        vedtakSomSkalBehandles.first().also {
            it.fnr `should be equal to` fnr
            it.behandletStatus `should be equal to` BehandletStatus.NY
            it.behandletTidspunkt `should be equal to` null
            it.fom `should be equal to` vedtaksperiode.fom
            it.tom `should be equal to` vedtaksperiode.tom
        }
    }

    @Test
    @Order(2)
    fun `Oppretter søknad fra med status FREMTIDIG`() {
        friskTilArbeidService.behandleFriskTilArbeidVedtakStatus(1)

        friskTilArbeidRepository.finnVedtakSomSkalBehandles(1).size `should be equal to` 0

        val friskTilArbeidDbRecord =
            friskTilArbeidRepository.findAll().first().also {
                it.behandletStatus `should be equal to` BehandletStatus.BEHANDLET
                it.behandletTidspunkt!! `should be within seconds of` (1 to Instant.now())
            }

        sykepengesoknadRepository.findByFriskTilArbeidVedtakId(friskTilArbeidDbRecord.id!!).also {
            it.size `should be equal to` 3
            it.forEach {
                it.status `should be equal to` Soknadstatus.FREMTIDIG
                it.friskTilArbeidVedtakId `should be equal to` friskTilArbeidDbRecord.id
            }
        }

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 3, Duration.ofSeconds(1)).tilSoknader().also { soknader ->
            soknader.size `should be equal to` 3
            soknader.forEach {
                it.fnr `should be equal to` fnr
                it.status `should be equal to` SoknadsstatusDTO.FREMTIDIG
                it.type `should be equal to` SoknadstypeDTO.FRISKMELDT_TIL_ARBEIDSFORMIDLING

                it.friskTilArbeidVedtakId `should be equal to` friskTilArbeidDbRecord.id
                it.friskTilArbeidVedtakPeriode `should be equal to`
                    Periode(
                        fom = friskTilArbeidDbRecord.fom,
                        tom = friskTilArbeidDbRecord.tom,
                    ).serialisertTilString()
            }
        }
    }
}
