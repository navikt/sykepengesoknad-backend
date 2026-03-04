package no.nav.helse.flex.client.texas

import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

data class TexasHentTokenRequest(
    val identity_provider: String,
    val target: String,
)

data class TexasHentTokenResponse(
    val access_token: String,
)

@Component
class TexasClient(
    private val texasRestClient: RestClient,
) {
    fun hentToken(
        identityProvider: String,
        target: String,
    ): TexasHentTokenResponse =
        texasRestClient
            .post()
            .uri { uriBuilder -> uriBuilder.build() }
            .headers {
                it.contentType = MediaType.APPLICATION_JSON
            }.body(
                TexasHentTokenRequest(
                    identity_provider = identityProvider,
                    target = target,
                ),
            ).retrieve()
            .toEntity<TexasHentTokenResponse>()
            .body
            ?: throw RuntimeException("Texas introspection mangler body")
}
