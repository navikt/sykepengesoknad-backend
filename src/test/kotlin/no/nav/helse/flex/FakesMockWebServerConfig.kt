package no.nav.helse.flex

import no.nav.helse.flex.mockdispatcher.EnhetsregisterMockDispatcher
import okhttp3.mockwebserver.MockWebServer
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
                    System.setProperty("BRREG_API_URL", "http://localhost:${it.port}")
                }

        val enhetsregisterMockWebServer =
            MockWebServer().apply {
                System.setProperty("ENHETSREGISTER_API_URL", "http://localhost:$port")
                dispatcher = EnhetsregisterMockDispatcher
            }
    }
}
