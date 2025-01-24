package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.subscribeHvisIkkeSubscribed
import no.nav.helse.flex.util.serialisertTilString
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*
import java.util.concurrent.TimeUnit

class FriskTilArbeidIntegrationTest() : FellesTestOppsett() {
    @Autowired
    lateinit var friskTilArbeidKafkaConsumer: Consumer<String, String>

    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @BeforeAll
    fun subscribeToTopic() {
        friskTilArbeidKafkaConsumer.subscribeHvisIkkeSubscribed(FRISKTILARBEID_TOPIC)
    }

    @BeforeEach
    fun slettFraDatabase() {
        friskTilArbeidRepository.deleteAll()
    }

    private val uuid = UUID.randomUUID().toString()
    private val fnr = "11111111111"

    @Test
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        val key = fnr.asProducerRecordKey()
        val friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET)

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

        val dbRecords =
            await().atMost(1, TimeUnit.SECONDS).until({ friskTilArbeidRepository.findAll().toList() }, { it.size == 1 })

        dbRecords.first().also {
            it.fnr `should be equal to` fnr
        }
    }

    @Test
    fun `Mottar men lagrer ikke VedtakStatusRecord med status FERDIG_BEHANDLET`() {
        val key = fnr.asProducerRecordKey()
        val friskTilArbeidVedtakStatus = lagFriskTilArbeidVedtakStatus(fnr, Status.FERDIG_BEHANDLET)

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

        await().during(100, TimeUnit.MILLISECONDS).until { friskTilArbeidRepository.findAll().toList().isEmpty() }
    }
}

fun String.asProducerRecordKey(): String = UUID.nameUUIDFromBytes(this.toByteArray()).toString()

fun lagFriskTilArbeidVedtakStatus(
    fnr: String,
    status: Status,
): FriskTilArbeidVedtakStatus =
    FriskTilArbeidVedtakStatus(
        uuid = UUID.randomUUID().toString(),
        personident = fnr,
        begrunnelse = "Begrunnelse",
        fom = LocalDate.now(),
        tom = LocalDate.now(),
        status = status,
        statusAt = OffsetDateTime.now(),
        statusBy = "Test",
    )
