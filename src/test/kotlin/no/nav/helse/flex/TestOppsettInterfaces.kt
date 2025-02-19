package no.nav.helse.flex

import no.nav.security.mock.oauth2.MockOAuth2Server
import org.apache.kafka.clients.producer.Producer
import org.springframework.test.web.servlet.MockMvc

interface TestOppsettInterfaces {
    fun server(): MockOAuth2Server

    fun mockMvc(): MockMvc

    fun kafkaProducer(): Producer<String, String>
}
