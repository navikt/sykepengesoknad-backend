package no.nav.syfo.kafka

import no.nav.syfo.util.OBJECT_MAPPER
import org.apache.kafka.common.serialization.Serializer

class JacksonKafkaSerializer<T : Any> : Serializer<T> {

    override fun serialize(topic: String?, data: T): ByteArray = OBJECT_MAPPER.writeValueAsBytes(data)

    override fun close() {}
}
