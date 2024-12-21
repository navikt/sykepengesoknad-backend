package no.nav.helse.flex.config

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

const val GRUNNBELOEP_API_CONNECT_TIMEOUT = 3L
const val GRUNNBELOEP_API_READ_TIMEOUT = 3L

@Configuration
class RestClientConfiguration {
    @Bean
    fun restClientBuilder(): RestClient.Builder {
        val connectionManager =
            PoolingHttpClientConnectionManager().apply {
                maxTotal = 10
                defaultMaxPerRoute = 10
            }

        val httpClient =
            HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .build()

        val requestFactory =
            HttpComponentsClientHttpRequestFactory(httpClient).apply {
                setConnectTimeout(Duration.ofSeconds(GRUNNBELOEP_API_CONNECT_TIMEOUT))
                setReadTimeout(Duration.ofSeconds(GRUNNBELOEP_API_READ_TIMEOUT))
            }

        return RestClient.builder()
            .requestFactory(requestFactory)
    }

    @Bean
    fun restClient(restClientBuilder: RestClient.Builder): RestClient = restClientBuilder.build()
}
