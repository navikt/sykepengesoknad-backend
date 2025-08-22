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
    restClientBuilder: RestClient.Builder,
    @Value("\${ENHETSREGISTER_BASE_URL:https://data.brreg.no/enhetsregisteret}")
    baseUrl: String,
) {
    private val restClient: RestClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .build()

    @Retryable(
        include = [HttpServerErrorException::class],
        maxAttemptsExpression = "\${BRREG_RETRY_ATTEMPTS:3}",
    )
    fun erDagmamma(orgnr: String): Boolean {
        val dagmammaKode = "88.912"

        try {
            // Deserialize the response body directly into a Map.
            val responseMap =
                restClient
                    .get()
                    .uri("/api/enheter/{orgnr}", orgnr)
                    .retrieve()
                    .body<Map<String, Any>>()

            if (responseMap == null) return false

            return responseMap
                // 1. Get all key-value pairs from the map.
                .entries
                // 2. Filter to find keys that start with "naeringskode".
                .filter { (key, _) -> key.startsWith("naeringskode") }
                // 3. Extract the actual code value (e.g., "88.912").
                .mapNotNull { (_, value) ->
                    // The value is a Map like {"kode": "...", ...}, so we cast and extract.
                    (value as? Map<*, *>)?.get("kode") as? String
                }
                // 4. Check if any of the found codes match our target.
                .any { it == dagmammaKode }
        } catch (e: HttpClientErrorException.NotFound) {
            return false
        }
    }

    @Retryable(
        include = [HttpServerErrorException::class],
        maxAttemptsExpression = "\${BRREG_RETRY_ATTEMPTS:3}",
    )
    fun hentEnhet(orgnr: String): EnhetDto? { // Return nullable DTO for clarity
        return try {
            restClient
                .get()
                // Use URI templates for safety and clarity
                .uri("/api/enheter/{orgnr}", orgnr)
                .retrieve()
                // Let RestClient handle JSON deserialization directly
                .body<EnhetDto>()
        } catch (e: HttpClientErrorException.NotFound) {
            // Gracefully handle 404 errors by returning null
            null
        }
    }
}
