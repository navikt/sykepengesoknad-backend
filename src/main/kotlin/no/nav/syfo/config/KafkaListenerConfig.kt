package no.nav.syfo.config

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.syfo.kafka.KafkaErrorHandler
import no.nav.syfo.kafka.soknad.deserializer.MultiFunctionDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.ConsumerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import java.time.Duration
import java.util.*
import java.util.Collections.emptyMap

@Configuration
@EnableKafka
class KafkaListenerConfig {

    @Bean
    fun schemaRegistryClient(@Value("\${kafka-schema-registry.url}") url: String): SchemaRegistryClient {
        return CachedSchemaRegistryClient(url, 20)
    }

    @Bean
    fun consumerFactoryPersonhendelse(
        schemaRegistryClient: SchemaRegistryClient,
        @Value("\${kafka-schema-registry.url}") url: String,
        properties: KafkaProperties
    ): ConsumerFactory<String, GenericRecord> {
        val config = HashMap<String, Any>()
        config[AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS] = false
        config[KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG] = false
        config[KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG] = url
        val kafkaAvroDeserializer = KafkaAvroDeserializer(schemaRegistryClient, config)

        return DefaultKafkaConsumerFactory(
            properties.buildConsumerProperties(),
            StringDeserializer(),
            MultiFunctionDeserializer(
                emptyMap()
            ) { bytes -> kafkaAvroDeserializer.deserialize("aapen-person-pdl-leesah-v1", bytes) as GenericRecord }
        )
    }

    @Bean
    fun kafkaListenerContainerFactory(
        consumerFactoryPersonhendelse: ConsumerFactory<String, GenericRecord>,
        kafkaErrorHandler: KafkaErrorHandler
    ): ConcurrentKafkaListenerContainerFactory<String, GenericRecord> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, GenericRecord>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        @Suppress("DEPRECATION")
        factory.setErrorHandler(kafkaErrorHandler)
        factory.containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(2))
        factory.consumerFactory = consumerFactoryPersonhendelse
        return factory
    }
}
