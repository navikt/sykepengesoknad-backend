package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.config.GRUNNBELOEP_API_CONNECT_TIMEOUT
import no.nav.helse.flex.config.RestClientConfiguration
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClient
import java.util.concurrent.TimeUnit

/**
 * Tester at konfigurasjonen av RestClient fungerer siden connectTimeout og readTimeout er eksplisitt satt.
 *
 * @see RestClientConfiguration
 */
@SpringBootTest(classes = [RestClientConfiguration::class])
class RestClientConfigurationTest {
    @Autowired
    private lateinit var restClient: RestClient

    private val mockWebServer: MockWebServer = MockWebServer()

    @Test
    fun failOnConnectTimeout() {
        mockWebServer.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))

        await().atMost(GRUNNBELOEP_API_CONNECT_TIMEOUT + 1, TimeUnit.SECONDS).untilAsserted {
            assertThrows<ResourceAccessException> {
                restClient.get().uri(mockWebServer.url("/").toString()).retrieve().toBodilessEntity()
            }
        }
    }

    @Test
    fun failOnReadTimeout() {
        mockWebServer.enqueue(MockResponse().setHeadersDelay(GRUNNBELOEP_API_CONNECT_TIMEOUT + 10, TimeUnit.SECONDS))

        await().atMost(GRUNNBELOEP_API_CONNECT_TIMEOUT + 1, TimeUnit.SECONDS).untilAsserted {
            assertThrows<ResourceAccessException> {
                restClient.get().uri(mockWebServer.url("/").toString()).retrieve().toBodilessEntity()
            }
        }
    }
}
