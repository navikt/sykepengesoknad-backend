package no.nav.helse.flex.kafka

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties
import java.time.Duration

@Configuration
class OnPremKafkaConfig(
    @Value("\${on-prem-kafka.bootstrap-servers}") private val kafkaBootstrapServers: String,
    @Value("\${on-prem-kafka.security-protocol}") private val kafkaSecurityProtocol: String,
    @Value("\${on-prem-kafka.username}") private val serviceuserUsername: String,
    @Value("\${on-prem-kafka.password}") private val serviceuserPassword: String,
    @Value("\${on-prem-kafka.auto-offset-reset:none}") private val kafkaAutoOffsetReset: String,
    @Value("\${on-prem-kafka.schema-registry}") private val kafkaSchemaRegistryUrl: String,
) {

    fun commonConfig() = mapOf(
        CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
        CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG to kafkaBootstrapServers,
        SaslConfigs.SASL_JAAS_CONFIG to "org.apache.kafka.common.security.plain.PlainLoginModule required username=\"${serviceuserUsername}\" password=\"${serviceuserPassword}\";",
        SaslConfigs.SASL_MECHANISM to "PLAIN",
    )

    fun genericAvroConsumerConfig() = mapOf(
        ConsumerConfig.GROUP_ID_CONFIG to "syfosoknad-consumer",
        ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
        ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
        AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS to false,
        KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to false,
        KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to kafkaSchemaRegistryUrl,
    ) + commonConfig()

    @Bean
    fun schemaRegistryClient(): SchemaRegistryClient {
        return CachedSchemaRegistryClient(kafkaSchemaRegistryUrl, 20)
    }

    @Bean
    fun kafkaListenerContainerFactory(
        schemaRegistryClient: SchemaRegistryClient,
        kafkaErrorHandler: AivenKafkaErrorHandler,
    ): ConcurrentKafkaListenerContainerFactory<String, GenericRecord> {
        val consumerFactory = DefaultKafkaConsumerFactory(
            genericAvroConsumerConfig(),
            StringDeserializer(),
            KafkaAvroDeserializer(schemaRegistryClient),
        )

        val factory = ConcurrentKafkaListenerContainerFactory<String, GenericRecord>()
        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
        factory.setCommonErrorHandler(kafkaErrorHandler)
        factory.containerProperties.setAuthExceptionRetryInterval(Duration.ofSeconds(2))
        factory.consumerFactory = consumerFactory
        return factory
    }
}
