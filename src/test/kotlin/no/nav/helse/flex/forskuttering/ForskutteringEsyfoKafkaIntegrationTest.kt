package no.nav.helse.flex.forskuttering

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.forskuttering.domain.NarmesteLederLeesah
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
import org.springframework.kafka.listener.MessageListenerContainer
import java.time.Duration
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ForskutteringEsyfoKafkaIntegrationTest : FellesTestOppsett() {
    @Autowired lateinit var register: KafkaListenerEndpointRegistry

    @Autowired lateinit var esyfo: ForskutteringEsyfoRepository

    @Autowired lateinit var gammel: ForskutteringRepository

    @Autowired lateinit var jdbc: JdbcTemplate

    @Autowired
    @Qualifier("esyfoKafkaListenerContainerFactory")
    lateinit var factory: ConcurrentKafkaListenerContainerFactory<String, String>

    @Autowired
    @Qualifier("aivenKafkaListenerContainerFactory")
    lateinit var eksisterendeFactory: ConcurrentKafkaListenerContainerFactory<String, String>

    private val gruppe = "forskuttering-esyfo"

    private fun checkpoint(): Long? =
        jdbc
            .query(
                "SELECT last_processed_offset FROM forskuttering_esyfo_checkpoint WHERE topic = ? AND partition = 0",
                { rs, _ -> rs.getLong(1) },
                NARMESTELEDER_ESYFO_LEESAH_TOPIC,
            ).firstOrNull()

    @Test
    fun `Egen Kafka-gruppe leser historikk isolert ved eksplisitt start og gjenstart`() {
        val container = checkNotNull(register.getListenerContainer(gruppe))
        kontrollerConsumerInnstillinger(container)
        val melding = getNarmesteLederLeesah(UUID.randomUUID(), fnr = "12345678945", orgnummer = "999999994")
        val historiskOffset = publiserHistorikk(melding)
        kontrollerAtBareEksisterendeTabellErOppdatert(melding)

        try {
            kontrollerHistorikkInnlesing(container, melding, historiskOffset)
            kontrollerGammelOppdateringMedAktivEsyfoConsumer(melding)
            val slettetOffset = kontrollerTombstone(melding)
            kontrollerGjenstartMedSammeGruppe(container, melding, slettetOffset)
        } finally {
            stoppConsumer(container)
        }
    }

    private fun kontrollerConsumerInnstillinger(container: MessageListenerContainer) {
        container.isRunning shouldBe false
        val config = checkNotNull(factory.consumerFactory).configurationProperties
        container.groupId shouldBe gruppe
        config[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] shouldBe "earliest"
        config[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] shouldBe false
        config[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe "500"
        factory.isBatchListener shouldBe true
        (eksisterendeFactory.isBatchListener == true) shouldBe false
        checkNotNull(eksisterendeFactory.consumerFactory).configurationProperties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe "1"
    }

    private fun publiserHistorikk(melding: NarmesteLederLeesah): Long {
        val key = melding.narmesteLederId.toString()
        kafkaProducer.send(ProducerRecord(NARMESTELEDER_LEESAH_TOPIC, 0, key, melding.serialisertTilString())).get()
        sendEsyfoMelding(melding.narmesteLederId, melding.serialisertTilString())
        return sendEsyfoMelding(melding.narmesteLederId, melding.copy(arbeidsgiverForskutterer = false).serialisertTilString())
    }

    private fun kontrollerAtBareEksisterendeTabellErOppdatert(melding: NarmesteLederLeesah) {
        await().atMost(Duration.ofSeconds(20)).until {
            gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer == true
        }
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        checkpoint().shouldBeNull()
    }

    private fun kontrollerHistorikkInnlesing(
        container: MessageListenerContainer,
        melding: NarmesteLederLeesah,
        historiskOffset: Long,
    ) {
        container.start()
        ventPaLagretForskuttering(melding.narmesteLederId, historiskOffset, forskutterer = false)
        gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
    }

    private fun kontrollerTombstone(melding: NarmesteLederLeesah): Long {
        val slettetOffset = sendEsyfoMelding(melding.narmesteLederId, null)
        await().atMost(Duration.ofSeconds(20)).until { checkpoint() == slettetOffset }
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
        return slettetOffset
    }

    private fun kontrollerGammelOppdateringMedAktivEsyfoConsumer(melding: NarmesteLederLeesah) {
        val lagretEsyfo = esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldNotBeNull()
        val lagretOffset = checkpoint()
        val oppdatert = melding.copy(orgnummer = "999999993", timestamp = melding.timestamp.minusDays(1))
        kafkaProducer
            .send(
                ProducerRecord(NARMESTELEDER_LEESAH_TOPIC, 0, melding.narmesteLederId.toString(), oppdatert.serialisertTilString()),
            ).get()
        await().atMost(Duration.ofSeconds(20)).untilAsserted {
            gammel.findByNarmesteLederId(melding.narmesteLederId)?.orgnummer shouldBe oppdatert.orgnummer
        }
        esyfo.findByNarmesteLederId(melding.narmesteLederId) shouldBe lagretEsyfo
        checkpoint() shouldBe lagretOffset
    }

    private fun kontrollerGjenstartMedSammeGruppe(
        container: MessageListenerContainer,
        melding: NarmesteLederLeesah,
        slettetOffset: Long,
    ) {
        stoppConsumer(container)
        val nesteOffset = sendEsyfoMelding(melding.narmesteLederId, melding.serialisertTilString())
        checkpoint() shouldBe slettetOffset
        container.start()
        ventPaLagretForskuttering(melding.narmesteLederId, nesteOffset, forskutterer = true)
        gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
    }

    private fun ventPaLagretForskuttering(
        id: UUID,
        offset: Long,
        forskutterer: Boolean,
    ) {
        await().atMost(Duration.ofSeconds(30)).until {
            checkpoint() == offset && esyfo.findByNarmesteLederId(id)?.arbeidsgiverForskutterer == forskutterer
        }
    }

    private fun stoppConsumer(container: MessageListenerContainer) {
        val stoppet = CountDownLatch(1)
        container.stop { stoppet.countDown() }
        check(stoppet.await(20, TimeUnit.SECONDS))
    }

    private fun sendEsyfoMelding(
        id: UUID,
        verdi: String?,
    ): Long = kafkaProducer.send(ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 0, id.toString(), verdi)).get().offset()
}
