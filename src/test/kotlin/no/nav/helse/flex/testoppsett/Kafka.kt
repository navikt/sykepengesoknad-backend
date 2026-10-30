package no.nav.helse.flex.testoppsett

import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

fun startKafkaContainer() {
    KafkaContainer(DockerImageName.parse("apache/kafka:4.3.1")).apply {
        start()
        System.setProperty("KAFKA_BROKERS", bootstrapServers)
    }
}
