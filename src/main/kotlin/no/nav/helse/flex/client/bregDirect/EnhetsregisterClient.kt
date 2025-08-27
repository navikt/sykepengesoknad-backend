package no.nav.helse.flex.client.bregDirect

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import kotlin.text.get

@Component
class EnhetsregisterClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${ENHETSREGISTER_BASE_URL:https://data.brreg.no/enhetsregisteret}")
    baseUrl: String,
) {
    private val log = logger()

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

        return try {
            val responseMap =
                restClient
                    .get()
                    .uri("/api/enheter/{orgnr}", orgnr)
                    .retrieve()
                    .body<Map<String, Any>>()
                    ?: throw HttpClientErrorException.NotFound.create(
                        HttpStatus.NOT_FOUND,
                        "Not Found",
                        HttpHeaders(),
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
