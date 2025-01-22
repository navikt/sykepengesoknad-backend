package no.nav.helse.flex.kafka.consumer

import no.nav.helse.flex.kafka.AivenKafkaConfig
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.core.DefaultKafkaConsumerFactory

@Configuration
class TestKafkaConfig(
    private val aivenKafkaConfig: AivenKafkaConfig,
) {
    @Bean
    fun friskTilArbeidKafkaConsumer(): Consumer<String, String> {
        return DefaultKafkaConsumerFactory(
            testConsumerProps("frisktilarbeid-consumer"),
            StringDeserializer(),
            StringDeserializer(),
        ).createConsumer()
    }

    fun testConsumerProps(groupId: String) =
        mapOf(
            ConsumerConfig.GROUP_ID_CONFIG to groupId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
        ) + aivenKafkaConfig.commonConfig()
}
