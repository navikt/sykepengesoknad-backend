package no.nav.helse.flex.client.bregDirect

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

enum class DagmammaStatus {
    JA,
    NEI,
    IKKE_FUNNET,
    SERVER_FEIL,
}

sealed class HentEnhetResult {
    data class Success(
        val enhet: EnhetDto,
    ) : HentEnhetResult()

    data object NotFound : HentEnhetResult()

    data object ServerError : HentEnhetResult()
}

@Component
class EnhetsregisterClient(
    restClientBuilder: RestClient.Builder,
    @Value("\${ENHETSREGISTER_BASE_URL:https://data.brreg.no/enhetsregisteret}")
    baseUrl: String,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val restClient: RestClient =
        restClientBuilder
            .baseUrl(baseUrl)
            .build()

    @Retryable(
        include = [HttpServerErrorException::class],
        maxAttemptsExpression = "\${BRREG_RETRY_ATTEMPTS:3}",
    )
    fun erDagmamma(orgnr: String): DagmammaStatus {
        val dagmammaKode = "88.912"

        try {
            val responseMap =
                restClient
                    .get()
                    .uri("/api/enheter/{orgnr}", orgnr)
                    .retrieve()
                    .body<Map<String, Any>>()

            if (responseMap == null) {
                logger.warn("Mottok null body for orgnr $orgnr, behandler dette som  NOT_FOUND.")
                return DagmammaStatus.IKKE_FUNNET
            }

            val isDagmamma =
                responseMap
                    .entries
                    .filter { (key, _) -> key.startsWith("naeringskode") }
                    .mapNotNull { (_, value) -> (value as? Map<*, *>)?.get("kode") as? String }
                    .any { it == dagmammaKode }

            return if (isDagmamma) DagmammaStatus.JA else DagmammaStatus.NEI
        } catch (e: HttpClientErrorException.NotFound) {
            logger.warn("Orgnr $orgnr ikke funnet i Enhetsregisteret.")
            return DagmammaStatus.IKKE_FUNNET
        } catch (e: HttpServerErrorException) {
            logger.error("Serverfeil etter retries for orgnr $orgnr.", e)
            return DagmammaStatus.SERVER_FEIL
        }
    }

    @Retryable(
        include = [HttpServerErrorException::class],
        maxAttemptsExpression = "\${BRREG_RETRY_ATTEMPTS:3}",
    )
    fun hentEnhet(orgnr: String): HentEnhetResult =
        try {
            val enhetDto =
                restClient
                    .get()
                    .uri("/api/enheter/{orgnr}", orgnr)
                    .retrieve()
                    .body<EnhetDto>()

            if (enhetDto == null) {
                logger.warn("Received null body for orgnr $orgnr when fetching enhet.")
                HentEnhetResult.NotFound
            } else {
                HentEnhetResult.Success(enhetDto)
            }
        } catch (e: HttpClientErrorException.NotFound) {
            logger.warn("Orgnr $orgnr not found when fetching enhet.")
            HentEnhetResult.NotFound
        } catch (e: HttpServerErrorException) {
            logger.error("Server error after retries for orgnr $orgnr when fetching enhet.", e)
            HentEnhetResult.ServerError
        }
}
