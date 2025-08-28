package no.nav.helse.flex.client.bregDirect

import no.nav.helse.flex.logger
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

@Component
class EnhetsregisterClient(
    private val enhetsregisterRestClient: RestClient,
) {
    private val log = logger()

    @Retryable(
        include = [HttpServerErrorException::class],
        maxAttemptsExpression = "\${BRREG_RETRY_ATTEMPTS:3}",
    )
    fun erDagmamma(orgnr: String): Boolean {
        val dagmammaKode = "88.912"

        return try {
            val responseMap =
                enhetsregisterRestClient
                    .get()
                    .uri("/api/enheter/{orgnr}", orgnr)
                    .retrieve()
                    .body<Map<String, Any>>()
                    ?: throw HttpClientErrorException.NotFound.create(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Not Found",
                        org.springframework.http.HttpHeaders(),
                        ByteArray(0),
                        null,
                    )

            responseMap.entries
                .filter { (key, _) -> key.startsWith("naeringskode") }
                .mapNotNull { (_, value) -> (value as? Map<*, *>)?.get("kode") as? String }
                .any { it == dagmammaKode }
        } catch (e: Exception) {
            when (e) {
                is HttpClientErrorException.NotFound -> {
                    log.warn("Orgnr $orgnr ikke funnet i Enhetsregisteret.")
                }
                is HttpServerErrorException -> {
                    log.error("Serverfeil etter retries for orgnr $orgnr.", e)
                }
                else -> {
                    log.error("Uventet feil ved oppslag for orgnr $orgnr.", e)
                }
            }
            throw e
        }
    }
}
