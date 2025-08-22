package no.nav.helse.flex.client.brreg

import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

@Component
class BrregClient(
    private val brregRestClient: RestClient,
) {
    @Retryable(include = [HttpServerErrorException::class], maxAttemptsExpression = "\${BRREG_RETRY_ATTEMPTS:3}")
    fun hentRoller(
        fnr: String,
        rolleTyper: List<Rolletype>? = null,
    ): List<RolleDto> {
        val uri = brregRestClient.post().uri { uriBuilder -> uriBuilder.path("/api/v1/roller").build() }
        val hentRollerRequest = HentRollerRequest(fnr = fnr, rolleTyper = rolleTyper)

        return uri
            .headers {
                it.contentType = MediaType.APPLICATION_JSON
            }.body(hentRollerRequest)
            .retrieve()
            .toEntity<RollerDto>()
            .body
            ?.roller ?: emptyList()
    }
}
