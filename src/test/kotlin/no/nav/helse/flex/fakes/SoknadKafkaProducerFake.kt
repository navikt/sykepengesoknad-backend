package no.nav.helse.flex.fakes

import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.consumer.ConsumerGroupMetadata
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.clients.producer.*
import org.apache.kafka.common.*
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit

@Repository
@Profile("fakes")
@Primary
class SoknadKafkaProducerFake : Producer<String, SykepengesoknadDTO> {
    companion object {
        val records = mutableListOf<ProducerRecord<String, SykepengesoknadDTO>>()
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
        p0: ProducerRecord<String, SykepengesoknadDTO>?,
        p1: Callback?,
    ): Future<RecordMetadata> {
        TODO("Not yet implemented")
    }

    override fun send(p0: ProducerRecord<String, SykepengesoknadDTO>): Future<RecordMetadata> {
        records.add(p0)
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
