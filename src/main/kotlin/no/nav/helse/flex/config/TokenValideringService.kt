package no.nav.helse.flex.config

import no.nav.helse.flex.client.texas.TexasClient
import org.springframework.stereotype.Service

@Service
class TokenValideringService(
    private val texasClient: TexasClient,
) {
    fun hentToken(
        target: String,
        identityProvider: String,
    ): String =
        texasClient
            .hentToken(
                identityProvider = identityProvider,
                target = target,
            ).access_token
}
