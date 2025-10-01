package no.nav.helse.flex.client.bregDirect

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.flex.util.objectMapper
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

const val NAERINGSKODE_BARNEPASSER = "88.912"

@Component
class EnhetsregisterClient(
    private val enhetsregisterRestClient: RestClient,
) {
    @Retryable(
        include = [HttpServerErrorException::class],
        maxAttemptsExpression = "\${CLIENT_RETRY_ATTEMPTS:3}",
    )
    fun erBarnepasser(organisasjonsnummer: String): Boolean {
        val response =
            enhetsregisterRestClient
                .get()
                .uri("/api/enheter/{orgnr}", organisasjonsnummer)
                .retrieve()
                .body(String::class.java)!!

        val root: JsonNode = objectMapper.readTree(response)

        return root
            .properties()
            .asSequence()
            .filter { (name, _) -> name.startsWith("naeringskode") }
            .mapNotNull { (_, jsonNode) -> jsonNode.get("kode")?.asText() }
            .any { it == NAERINGSKODE_BARNEPASSER }
    }
}
