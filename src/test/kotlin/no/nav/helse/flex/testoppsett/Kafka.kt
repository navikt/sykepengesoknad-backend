package no.nav.helse.flex.testoppsett

import org.testcontainers.utility.DockerImageName

@Suppress("DEPRECATION")
fun startKafkaContainer() {
    org.testcontainers.containers.KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).apply {
        start()
        System.setProperty("KAFKA_BROKERS", bootstrapServers)
    }
}
