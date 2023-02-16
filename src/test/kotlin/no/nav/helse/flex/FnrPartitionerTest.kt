package no.nav.helse.flex

import no.nav.helse.flex.kafka.AivenKafkaConfig
import no.nav.helse.flex.kafka.FnrPartitioner
import org.amshove.kluent.shouldBe
import org.amshove.kluent.shouldHaveSize
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder
import org.springframework.kafka.core.KafkaAdmin

private const val TEST_TOPIC = "TEST_TOPIC"
private const val ANTALL_PARTISJONER = 10
private const val REPETER_TEST_ANTALL_GANGER = 3

class FnrPartitionerTest : BaseTestClass() {

    @Autowired
    @Qualifier("partisjonert")
    override lateinit var kafkaProducer: KafkaProducer<String, String>

    @Test
    fun `Alle meldinger med samme fnr skal sendes på samme partisjon`() {
        val syntetiskeFnr = hentSyntetiskeFnr()
        val partisjoneringsResultat: HashMap<String, MutableList<Int>?> = hashMapOf()

        repeat(REPETER_TEST_ANTALL_GANGER) {
            syntetiskeFnr.forEach {
                val partition = kafkaProducer.send(ProducerRecord(TEST_TOPIC, it, "value_$it")).get().partition()
                partisjoneringsResultat.getOrPut(it) { mutableListOf() }?.add(partition)
            }
        }

        // All fnr skal ha blitt partisjonert til samme partisjon.
        partisjoneringsResultat.forEach {
            it.value!! shouldHaveSize REPETER_TEST_ANTALL_GANGER
            it.value!!.toSet().size shouldBe 1
        }

        // Alle partisjoner skal ha blitt brukt.
        partisjoneringsResultat.flatMap { listOf(it.value!!.first()) }
            .groupBy { it }
            .mapValues { it.value.size }
            .shouldHaveSize(ANTALL_PARTISJONER)
    }

    private fun hentSyntetiskeFnr() =
        this.javaClass.classLoader.getResourceAsStream("syntetiske-fnr.txt")!!.bufferedReader().readLines()
}

@Configuration
class TestKafkaConfig(private val aivenKafkaConfig: AivenKafkaConfig) {

    @Bean
    fun admin(): KafkaAdmin? {
        return KafkaAdmin(aivenKafkaConfig.commonConfig())
    }

    @Bean
    fun testTopic(): NewTopic? {
        return TopicBuilder.name(TEST_TOPIC)
            .partitions(ANTALL_PARTISJONER)
            .build()
    }

    @Bean("partisjonert")
    fun kafkaProducer(): KafkaProducer<String, String> {
        val config = mapOf(
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.PARTITIONER_CLASS_CONFIG to TestPartitioner::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 0,
        ) + aivenKafkaConfig.commonConfig()
        return KafkaProducer(config)
    }
}

class TestPartitioner : FnrPartitioner()
