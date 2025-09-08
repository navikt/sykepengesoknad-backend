package no.nav.helse.flex.client.bregDirect

import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

@Component
class EnhetsregisterClient(
    private val enhetsregisterRestClient: RestClient,
) {
    @Retryable(
        include = [HttpServerErrorException::class],
        maxAttemptsExpression = "\${BRREG_RETRY_ATTEMPTS:3}",
    )
    fun erDagmamma(orgnr: String): Boolean {
        val dagmammaKode = "88.912"

        val responseMap =
            enhetsregisterRestClient
                .get()
                .uri("/api/enheter/{orgnr}", orgnr)
                .retrieve()
                .toEntity<Map<String, Any>>()
                .body

        return responseMap
            ?.entries
            ?.filter { (key, _) -> key.startsWith("naeringskode") }
            ?.mapNotNull { (_, value) -> (value as? Map<*, *>)?.get("kode") as? String }
            ?.any { it == dagmammaKode }
            ?: false
    }
}
