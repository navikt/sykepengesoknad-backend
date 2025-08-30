package no.nav.helse.flex

import okhttp3.mockwebserver.MockWebServer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean

@TestConfiguration
class MockWebServereConfig {
    @Bean
    fun brregMockWebServer() = brregMocWebkServer

    companion object {
        private val log = logger()

        init {
            log.info("Starter brregMocWebkServer")
        }

        val brregMocWebkServer =
            MockWebServer()
                .also {
                    System.setProperty("BRREG_API_URL", "http://localhost:${it.port}")
                }
    }
}
