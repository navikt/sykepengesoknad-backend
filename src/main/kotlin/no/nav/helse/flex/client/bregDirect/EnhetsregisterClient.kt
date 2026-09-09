package no.nav.helse.flex.client.bregDirect

import no.nav.helse.flex.util.objectMapper
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.body
import tools.jackson.databind.JsonNode

const val NAERINGSKODE_BARNEPASSER = "88.912"

@Component
class EnhetsregisterClient(
    private val enhetsregisterRestClient: RestClient,
) {
    // maxRetries teller forsøk etter det initielle kallet, så dette gir 3 kall totalt.
    @Retryable(includes = [HttpServerErrorException::class], maxRetries = 2)
    fun erBarnepasser(organisasjonsnummer: String): Boolean {
        val response =
            enhetsregisterRestClient
                .get()
                .uri("/api/enheter/{orgnr}", organisasjonsnummer)
                .retrieve()
                .body<String>()!!

        val root: JsonNode = objectMapper.readTree(response)

        return root
            .properties()
            .asSequence()
            .filter { (name, _) -> name.startsWith("naeringskode") }
            .mapNotNull { (_, jsonNode) -> jsonNode.get("kode")?.asString() }
            .any { it == NAERINGSKODE_BARNEPASSER }
    }
}
