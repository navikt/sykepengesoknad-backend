package no.nav.helse.flex.kafka

import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import org.apache.kafka.clients.producer.internals.DefaultPartitioner
import org.apache.kafka.common.Cluster

class FnrPartitioner : DefaultPartitioner() {
    override fun partition(topic: String, key: Any, keyBytes: ByteArray, value: Any, valueBytes: ByteArray, cluster: Cluster, numPartitions: Int): Int {
        val soknad = value as SykepengesoknadDTO
        val actualKey: String = soknad.fnr
        return super.partition(topic, actualKey, actualKey.toByteArray(), value, valueBytes, cluster, numPartitions)
    }
}
