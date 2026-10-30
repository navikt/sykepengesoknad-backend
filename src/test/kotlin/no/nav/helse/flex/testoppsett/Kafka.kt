package no.nav.helse.flex.testoppsett

import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName

fun startKafkaContainer() {
    val kafkaLogg = StringBuffer()
    KafkaContainer(DockerImageName.parse("apache/kafka-native:4.3.2"))
        .withLogConsumer { kafkaLogg.append(it.utf8String) }
        .apply {
            try {
                start()
            } catch (e: Exception) {
                throw IllegalStateException("Kafka-containeren startet ikke. Container-logg:\n$kafkaLogg", e)
            }
            System.setProperty("KAFKA_BROKERS", bootstrapServers)
        }
}
