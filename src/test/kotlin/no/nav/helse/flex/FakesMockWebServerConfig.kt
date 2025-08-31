package no.nav.helse.flex

import no.nav.helse.flex.mockdispatcher.VentetidMockDispatcher
import okhttp3.mockwebserver.MockWebServer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class FakesMockWebServerConfig {
    @Bean("brregMockWebServer")
    fun lagBrregMockWebServer() = brregMockWebServer

    @Bean("ventetidMockWebServer")
    fun lagVentetidMockWebServer() = ventetidMockWebServer

    companion object {
        val brregMockWebServer =
            MockWebServer()
                .also {
                    System.setProperty("BRREG_API_URL", "http://localhost:${it.port}")
                }

        val ventetidMockWebServer =
            MockWebServer().apply {
                System.setProperty("flex.syketilfelle.url", "http://localhost:$port")
                dispatcher = VentetidMockDispatcher
            }
    }
}
