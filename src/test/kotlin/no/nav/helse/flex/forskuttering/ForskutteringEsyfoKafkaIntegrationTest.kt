package no.nav.helse.flex.forskuttering

import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.config.KafkaListenerEndpointRegistry
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
    fun `Ny Kafka-gruppe er av som standard og spiller historikk isolert ved eksplisitt start og restart`() {
        val container = checkNotNull(register.getListenerContainer(gruppe))
        container.isRunning shouldBe false
        val config = checkNotNull(factory.consumerFactory).configurationProperties
        container.groupId shouldBe gruppe
        config[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] shouldBe "earliest"
        config[ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG] shouldBe false
        config[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe "500"
        factory.isBatchListener shouldBe true
        (eksisterendeFactory.isBatchListener == true) shouldBe false
        checkNotNull(eksisterendeFactory.consumerFactory).configurationProperties[ConsumerConfig.MAX_POLL_RECORDS_CONFIG] shouldBe "1"

        val melding = getNarmesteLederLeesah(UUID.randomUUID(), fnr = "12345678945", orgnummer = "999999994")
        val key = melding.narmesteLederId.toString()
        kafkaProducer.send(ProducerRecord(NARMESTELEDER_LEESAH_TOPIC, 0, key, melding.serialisertTilString())).get()
        kafkaProducer.send(ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 0, key, melding.serialisertTilString())).get()
        val historisk =
            kafkaProducer
                .send(
                    ProducerRecord(
                        NARMESTELEDER_ESYFO_LEESAH_TOPIC,
                        0,
                        key,
                        melding.copy(arbeidsgiverForskutterer = false).serialisertTilString(),
                    ),
                ).get()
                .offset()

        await().atMost(Duration.ofSeconds(20)).until {
            gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer == true
        }
        esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
        checkpoint().shouldBeNull()
        val admin = AdminClient.create(mapOf(BOOTSTRAP_SERVERS_CONFIG to System.getProperty("KAFKA_BROKERS")))
        val partisjon = TopicPartition(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 0)
        try {
            container.start()
            await().atMost(Duration.ofSeconds(30)).until {
                checkpoint() == historisk && esyfo.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer == false
            }
            gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
            await().atMost(Duration.ofSeconds(20)).until {
                admin
                    .listConsumerGroupOffsets(gruppe)
                    .partitionsToOffsetAndMetadata()
                    .get()[partisjon]
                    ?.offset() == historisk + 1
            }

            val sletting =
                kafkaProducer.send(ProducerRecord<String, String?>(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 0, key, null)).get().offset()
            await().atMost(Duration.ofSeconds(20)).until { checkpoint() == sletting }
            esyfo.findByNarmesteLederId(melding.narmesteLederId).shouldBeNull()
            gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true

            val stoppet = CountDownLatch(1)
            container.stop { stoppet.countDown() }
            check(stoppet.await(20, TimeUnit.SECONDS))
            val neste =
                kafkaProducer.send(ProducerRecord(NARMESTELEDER_ESYFO_LEESAH_TOPIC, 0, key, melding.serialisertTilString())).get().offset()
            checkpoint() shouldBe sletting
            container.start()
            await().atMost(Duration.ofSeconds(30)).until {
                checkpoint() == neste && esyfo.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer == true
            }
            await().atMost(Duration.ofSeconds(20)).until {
                admin
                    .listConsumerGroupOffsets(gruppe)
                    .partitionsToOffsetAndMetadata()
                    .get()[partisjon]
                    ?.offset() == neste + 1
            }
            gammel.findByNarmesteLederId(melding.narmesteLederId)?.arbeidsgiverForskutterer shouldBe true
        } finally {
            val stoppet = CountDownLatch(1)
            container.stop { stoppet.countDown() }
            check(stoppet.await(20, TimeUnit.SECONDS))
            admin.close()
        }
    }
}
