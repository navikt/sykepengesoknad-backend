package no.nav.helse.flex.client.brreg

import no.nav.helse.flex.logger
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

@Component
class BrregClient(
    private val brregRestClient: RestClient,
) {
    val log = logger()

    @Retryable(include = [HttpServerErrorException::class])
    fun hentRoller(
        fnr: String,
        rolleTyper: List<Rolletype>? = null,
    ): List<RolleDto> {
        try {
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
        } catch (e: HttpClientErrorException) {
            log.error("Brreg returnerte ${e.statusCode}. Returnerer tom liste.")
            return emptyList()
        }
    }
}
