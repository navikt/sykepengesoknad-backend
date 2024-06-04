package no.nav.helse.flex.kafka

import no.nav.helse.flex.util.objectMapper
import org.apache.kafka.common.serialization.Serializer

class JacksonKafkaSerializer<T : Any> : Serializer<T> {
    override fun serialize(
        topic: String?,
        data: T,
    ): ByteArray = objectMapper.writeValueAsBytes(data)

    override fun close() {}
}
