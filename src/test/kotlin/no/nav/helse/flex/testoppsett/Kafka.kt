package no.nav.helse.flex.testoppsett

import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

fun startKafkaContainer() {
    KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.6.1")).apply {
        start()
        System.setProperty("KAFKA_BROKERS", bootstrapServers)
    }
}
