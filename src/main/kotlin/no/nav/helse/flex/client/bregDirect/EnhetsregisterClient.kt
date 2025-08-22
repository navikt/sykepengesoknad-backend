package no.nav.helse.flex.client.bregDirect

import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class EnhetsregisterClient(
    // Injects RestClient.Builder to configure the client locally.
    restClientBuilder: RestClient.Builder,
    @Value("\${ENHETSREGISTER_BASE_URL:https://data.brreg.no/enhetsregisteret}")
    baseUrl: String,
) {
    private val restClient: RestClient = restClientBuilder
        .baseUrl(baseUrl)
        .build()

    /**
     * Fetches unit information from the Brønnøysund Register Centre.
     * It first attempts to find a main unit ('enhet') and, if not found (HTTP 404),
     * falls back to searching for a subunit ('underenhet').
     *
     * @param orgnummer The organization number to look up.
     * @return The raw JSON response as a String.
     * @throws HttpClientErrorException.NotFound if the organization number is not found as either a main unit or a subunit.
     */
    fun hentEnhet(orgnummer: String): String {
        return try {
            // First, try fetching as a main unit.
            hentEnhetFraEndpoint("/api/enheter/$orgnummer")
        } catch (e: HttpClientErrorException.NotFound) {
            // If not found, fall back to fetching as a subunit.
            hentEnhetFraEndpoint("/api/underenheter/$orgnummer")
        }
    }

    /**
     * Performs the actual GET request to a specific path and is retryable on server errors.
     */
    @Retryable(
        include = [HttpServerErrorException::class],
        maxAttemptsExpression = "\${BRREG_RETRY_ATTEMPTS:3}"
    )
    private fun hentEnhetFraEndpoint(path: String): String {
        return restClient.get()
            .uri(path)
            .retrieve()
            .body<String>()
            ?: throw IllegalStateException("Received an empty body from Brønnøysundregistrene for path: $path")
    }
}
