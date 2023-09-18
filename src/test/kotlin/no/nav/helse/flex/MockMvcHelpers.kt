package no.nav.helse.flex

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import java.util.*

fun BaseTestClass.buildAzureClaimSet(
    subject: String,
    issuer: String = "azureator",
    audience: String = "syfosoknad-client-id"
): String {
    val claims = HashMap<String, String>()

    return server.token(
        subject = "Vi sjekker azp",
        issuerId = issuer,
        clientId = subject,
        audience = audience,
        claims = claims
    )
}

fun BaseTestClass.skapAzureJwt(subject: String = "sykepengesoknad-korrigering-metrikk-client-id") =
    buildAzureClaimSet(subject = subject)

fun MockOAuth2Server.token(
    subject: String,
    issuerId: String = "selvbetjening",
    clientId: String = UUID.randomUUID().toString(),
    audience: String = "loginservice-client-id",
    claims: Map<String, Any> = mapOf("acr" to "Level4")

): String {
    return this.issueToken(
        issuerId,
        clientId,
        DefaultOAuth2TokenCallback(
            issuerId = issuerId,
            subject = subject,
            audience = listOf(audience),
            claims = claims,
            expiry = 3600
        )
    ).serialize()
}
