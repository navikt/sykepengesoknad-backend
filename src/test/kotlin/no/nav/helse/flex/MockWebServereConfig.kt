package no.nav.helse.flex

import okhttp3.mockwebserver.MockWebServer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class MockWebServereConfig {
    @Bean
    fun brregServer() = brregServer

    companion object {
        private val logger = logger()

        init {
            logger.info("[TEST] Starter mock webservere")
        }

        val brregServer =
            MockWebServer()
                .also {
                    System.setProperty("BRREG_API_URL", "http://localhost:${it.port}")
                }
    }
}
