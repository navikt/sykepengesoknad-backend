package no.nav.helse.flex.kafka

import no.nav.helse.flex.aktivering.kafka.AktiveringBestilling
import no.nav.helse.flex.logger
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.producer.Partitioner
import org.apache.kafka.common.Cluster
import org.apache.kafka.common.InvalidRecordException
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.utils.Utils

abstract class FnrPartitioner : Partitioner {

    private val log = logger()

    override fun configure(configs: MutableMap<String, *>?) {}

    override fun close() {}

    override fun partition(
        topic: String?,
        key: Any?,
        keyBytes: ByteArray?,
        value: Any?,
        valueBytes: ByteArray?,
        cluster: Cluster?
    ): Int {
        val partitions: List<PartitionInfo> = cluster!!.partitionsForTopic(topic)
        val numPartitions: Int = partitions.size

        if (keyBytes == null || key !is String) {
            throw InvalidRecordException("All messages should have a valid key.")
        }

        val partition = Utils.toPositive(Utils.murmur2(keyBytes)) % (numPartitions)

        log.debug("NJM: numPartitions: $numPartitions, partition: $partition, key: $key")
        return partition
    }
}

class SykepengesoknadPartitioner : FnrPartitioner() {

    override fun partition(
        topic: String?,
        key: Any?,
        keyBytes: ByteArray?,
        value: Any?,
        valueBytes: ByteArray?,
        cluster: Cluster?
    ): Int {
        val soknad = value as SykepengesoknadDTO
        val actualKey: String = soknad.fnr
        return super.partition(topic, actualKey, actualKey.toByteArray(), value, valueBytes, cluster)
    }
}

class AktiveringBestillingPartitioner : FnrPartitioner() {

    override fun partition(
        topic: String?,
        key: Any?,
        keyBytes: ByteArray?,
        value: Any?,
        valueBytes: ByteArray?,
        cluster: Cluster?,
    ): Int {
        val aktiveringBestilling = value as AktiveringBestilling
        val actualKey: String = aktiveringBestilling.fnr
        return super.partition(topic, actualKey, actualKey.toByteArray(), value, valueBytes, cluster)
    }
}
