package no.nav.helse.flex.frisktilarbeid

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.kafka.FRISKTILARBEID_TOPIC
import no.nav.helse.flex.util.serialisertTilString
import org.amshove.kluent.`should be equal to`
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import java.util.concurrent.TimeUnit

class FriskTilArbeidTestDataIntegrationTest() : FellesTestOppsett() {
    @Autowired
    private lateinit var friskTilArbeidRepository: FriskTilArbeidRepository

    @BeforeEach
    fun slettFraDatabase() {
        friskTilArbeidRepository.deleteAll()
    }

    private val uuid = UUID.randomUUID().toString()
    private val fnr = "11111111111"

    @Test
    fun `Mottar og lagrer VedtakStatusRecord med status FATTET på testdata-topic`() {
        val key = fnr.asProducerRecordKey()

        kafkaProducer.send(
            ProducerRecord(
                FRISKTILARBEID_TOPIC,
                key,
                lagFriskTilArbeidVedtakStatus(fnr, Status.FATTET).serialisertTilString(),
            ),
        ).get()

        val dbRecords =
            await().atMost(1, TimeUnit.SECONDS).until({ friskTilArbeidRepository.findAll().toList() }, { it.size == 1 })

        dbRecords.first().also {
            it.fnr `should be equal to` fnr
        }
    }
}
