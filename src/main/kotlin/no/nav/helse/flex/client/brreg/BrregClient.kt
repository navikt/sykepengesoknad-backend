package no.nav.helse.flex.client.brreg

import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.toEntity

@Component
class BrregClient(
    private val brregRestClient: RestClient,
) {
    fun hentRoller(
        fnr: String,
        rolleTyper: List<Rolletype>? = null,
    ): List<RolleDto> {
        val uri = brregRestClient.post().uri { uriBuilder -> uriBuilder.path("/api/v1/roller").build() }
        val hentRollerRequest = HentRollerRequest(fnr = fnr, rolleTyper = rolleTyper)
        return uri
            .body(hentRollerRequest)
            .retrieve()
            .toEntity<RollerDto>()
            .body
            ?.roller ?: emptyList()
    }
}
