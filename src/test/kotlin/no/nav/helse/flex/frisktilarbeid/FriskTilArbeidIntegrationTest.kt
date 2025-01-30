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

    @Test
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET`() {
        val fnr = "11111111111"
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
            it.behandletStatus `should be equal to` BehandletStatus.NY
        }
    }
}
