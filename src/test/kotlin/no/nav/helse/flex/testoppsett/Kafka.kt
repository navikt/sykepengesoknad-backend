package no.nav.helse.flex.testoppsett

import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

fun startKafkaContainer() {
    KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).apply {
        withEnv("KAFKA_LOG_FLUSH_INTERVAL_MESSAGES", "1")
        withEnv("KAFKA_MIN_INSYNC_REPLICAS", "1")
        withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1")
        start()
        System.setProperty("KAFKA_BROKERS", bootstrapServers)
    }
}
