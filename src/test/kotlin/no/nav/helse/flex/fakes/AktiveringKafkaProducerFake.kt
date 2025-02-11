package no.nav.helse.flex.fakes

import no.nav.helse.flex.aktivering.AktiveringBestilling
import no.nav.helse.flex.aktivering.AktiveringConsumer
import no.nav.helse.flex.domain.*
import no.nav.helse.flex.repository.*
import no.nav.helse.flex.util.serialisertTilString
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Repository
@Profile("fakes")
@Primary
class AktiveringKafkaProducerFake : Producer<String, AktiveringBestilling> {
    @Autowired
    private lateinit var aktiveringConsumer: AktiveringConsumer

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
        p0: ProducerRecord<String, AktiveringBestilling>?,
        p1: Callback?,
    ): Future<RecordMetadata> {
        TODO("Not yet implemented")
    }

    override fun send(p0: ProducerRecord<String, AktiveringBestilling>): Future<RecordMetadata> {
        val cr = ConsumerRecord("topic", 0, 0, p0.key(), p0.value().serialisertTilString())
        aktiveringConsumer.listen(cr, { })
        return object : Future<RecordMetadata> {
            override fun cancel(p0: Boolean): Boolean {
                return false
            }

            override fun isCancelled(): Boolean {
                return false
            }

            override fun isDone(): Boolean {
                return true
            }

            override fun get(): RecordMetadata {
                @Suppress("DEPRECATION")
                return RecordMetadata(
                    TopicPartition("topic", 0),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                )
            }

            override fun get(
                p0: Long,
                p1: TimeUnit,
            ): RecordMetadata {
                @Suppress("DEPRECATION")
                return RecordMetadata(
                    TopicPartition("topic", 0),
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                )
            }
        }
    }
}
