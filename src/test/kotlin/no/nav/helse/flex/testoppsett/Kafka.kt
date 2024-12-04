package no.nav.helse.flex.testoppsett

import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

fun startKafkaContainer() {
    KafkaContainer(DockerImageName.parse("apache/kafka-native")).apply {
        start()
        System.setProperty("KAFKA_BROKERS", bootstrapServers)
    }
}
