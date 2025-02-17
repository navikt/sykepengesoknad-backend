package no.nav.helse.flex.config

import no.nav.security.token.support.client.core.ClientProperties
import no.nav.security.token.support.client.core.oauth2.OAuth2AccessTokenService
import no.nav.security.token.support.client.spring.ClientConfigurationProperties
import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.time.Duration

const val API_CONNECT_TIMEOUT = 3L
const val API_READ_TIMEOUT = 3L

@EnableOAuth2Client(cacheEnabled = true)
@Configuration
class RestClientConfig {
    @Bean
    fun brregRestClient(
        @Value("\${BRREG_API_URL}")
        url: String,
        oAuth2AccessTokenService: OAuth2AccessTokenService,
        clientConfigurationProperties: ClientConfigurationProperties,
    ): RestClient =
        restClientBuilder(
            oAuth2AccessTokenService = oAuth2AccessTokenService,
            clientConfigurationProperties = clientConfigurationProperties,
            registrationName = "flex-brreg-proxy-client-credentials",
        ).baseUrl(url).build()

    fun restClientBuilder(
        oAuth2AccessTokenService: OAuth2AccessTokenService,
        clientConfigurationProperties: ClientConfigurationProperties,
        registrationName: String,
    ): RestClient.Builder {
        val connectionManager =
            PoolingHttpClientConnectionManager().apply {
                maxTotal = 10
                defaultMaxPerRoute = 10
            }

        val httpClient =
            HttpClientBuilder
                .create()
                .setConnectionManager(connectionManager)
                .build()

        val requestFactory =
            HttpComponentsClientHttpRequestFactory(httpClient).apply {
                setConnectTimeout(Duration.ofSeconds(API_CONNECT_TIMEOUT))
                setReadTimeout(Duration.ofSeconds(API_READ_TIMEOUT))
            }

        val clientProperties =
            clientConfigurationProperties.registration[registrationName]
                ?: throw RuntimeException("Fant ikke config for $registrationName.")

        return RestClient
            .builder()
            .requestFactory(requestFactory)
            .requestInterceptor(BearerTokenInterceptor(oAuth2AccessTokenService, clientProperties))
    }
}

class BearerTokenInterceptor(
    private val oAuth2AccessTokenService: OAuth2AccessTokenService,
    private val clientProperties: ClientProperties,
) : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution,
    ): ClientHttpResponse {
        val response = oAuth2AccessTokenService.getAccessToken(clientProperties)
        response.access_token?.let { request.headers.setBearerAuth(it) }
        return execution.execute(request, body)
    }
}
