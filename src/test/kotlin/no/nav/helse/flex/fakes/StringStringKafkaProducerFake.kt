package no.nav.helse.flex.fakes
import no.nav.helse.flex.frisktilarbeid.ArbeidssokerregisterStoppListener
import no.nav.helse.flex.kafka.ARBEIDSSOKERREGISTER_STOPP_TOPIC
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Component
@Profile("fakes")
@Primary
class StringStringKafkaProducerFake : Producer<String, String> {
    companion object {
        val records = mutableListOf<ProducerRecord<String, String>>()
    }

    @Autowired
    lateinit var arbeidssokerregisterStoppListener: ArbeidssokerregisterStoppListener

    fun rutMeldingTilListener(record: ProducerRecord<String, String>) {
        if (record.topic() == ARBEIDSSOKERREGISTER_STOPP_TOPIC) {
            arbeidssokerregisterStoppListener.listen(record.tilConsumerRecord(), { })
        }
    }

    override fun close() {
        TODO("Not yet implemented")
    }

    override fun close(p0: Duration?) {
        TODO("Not yet implemented")
    }

    override fun initTransactions() {
        TODO("Not yet implemented")
    }

    override fun beginTransaction() {
        TODO("Not yet implemented")
    }

    @Deprecated("Deprecated", ReplaceWith("sendOffsetsToTransaction(p0, p1)"))
    override fun sendOffsetsToTransaction(
        p0: MutableMap<TopicPartition, OffsetAndMetadata>?,
        p1: String?,
    ) {
        TODO("Not yet implemented")
    }

    override fun sendOffsetsToTransaction(
        p0: MutableMap<TopicPartition, OffsetAndMetadata>?,
        p1: ConsumerGroupMetadata?,
    ) {
        TODO("Not yet implemented")
    }

    override fun commitTransaction() {
        TODO("Not yet implemented")
    }

    override fun abortTransaction() {
        TODO("Not yet implemented")
    }

    override fun flush() {
        TODO("Not yet implemented")
    }

    override fun partitionsFor(p0: String?): MutableList<PartitionInfo> {
        TODO("Not yet implemented")
    }

    override fun metrics(): MutableMap<MetricName, out Metric> {
        TODO("Not yet implemented")
    }

    override fun clientInstanceId(p0: Duration?): Uuid {
        TODO("Not yet implemented")
    }

    override fun send(
        p0: ProducerRecord<String, String>?,
        p1: Callback?,
    ): Future<RecordMetadata> {
        TODO("Not yet implemented")
    }

    override fun send(p0: ProducerRecord<String, String>): Future<RecordMetadata> {
        records.add(p0)
        rutMeldingTilListener(p0)
        return object : Future<RecordMetadata> {
            override fun cancel(p0: Boolean): Boolean = false

            override fun isCancelled(): Boolean = false

            override fun isDone(): Boolean = true

            override fun get(): RecordMetadata =
                RecordMetadata(
                    TopicPartition("topic", 0),
                    0,
                    0,
                    0,
                    0,
                    0,
                )

            override fun get(
                p0: Long,
                p1: TimeUnit,
            ): RecordMetadata =
                RecordMetadata(
                    TopicPartition("topic", 0),
                    0,
                    0,
                    0,
                    0,
                    0,
                )
        }
    }
}

private fun <K, V> ProducerRecord<K, V>.tilConsumerRecord(): ConsumerRecord<K, V> = ConsumerRecord(topic(), 1, 1, key(), value())
