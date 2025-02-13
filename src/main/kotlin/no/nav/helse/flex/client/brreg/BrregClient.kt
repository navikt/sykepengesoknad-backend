package no.nav.helse.flex.client.brreg

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

@Component
class BrregClient(
    @Value("\${BRREG_API_URL}")
    private val url: String,
    restClientBuilder: RestClient.Builder,
) {
    private val restClient = restClientBuilder.baseUrl(url).build()

    fun hentRoller(
        fnr: String,
        rolleTyper: List<Rolletype>? = null,
    ): List<RolleDto> {
        val uri = restClient.post().uri { uriBuilder -> uriBuilder.path("/roller").build() }
        val hentRollerRequest = HentRollerRequest(fnr = fnr, rolleTyper = rolleTyper)
        return uri
            .body(hentRollerRequest)
            .retrieve()
            .toEntity<RollerDto>()
            .body
            ?.roller ?: emptyList()
    }
}
