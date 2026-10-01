package no.nav.helse.flex

import mockwebserver3.MockWebServer
import no.nav.helse.flex.mockdispatcher.EnhetsregisterMockDispatcher
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class FakesMockWebServerConfig {
    @Bean("brregMockWebServer")
    fun lagBrregMockWebServer() = brregMockWebServer

    @Bean("enhetsregisterMockWebServer")
    fun lagEnhetsregisterMockWebServer() = enhetsregisterMockWebServer

    companion object {
        val brregMockWebServer =
            MockWebServer()
                .also {
                    it.start()
                    System.setProperty("BRREG_API_URL", "http://localhost:${it.port}")
                }

        val enhetsregisterMockWebServer =
            MockWebServer().apply {
                start()
                System.setProperty("ENHETSREGISTER_API_URL", "http://localhost:$port")
                dispatcher = EnhetsregisterMockDispatcher
            }
    }
}
