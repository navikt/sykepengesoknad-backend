package no.nav.helse.flex

import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import java.util.*

fun FellesTestOppsett.buildAzureClaimSet(
    subject: String,
    issuer: String = "azureator",
    audience: String = "syfosoknad-client-id",
    claims: HashMap<String, String> = hashMapOf(),
): String =
    server.token(
        subject = "Vi sjekker azp",
        issuerId = issuer,
        clientId = subject,
        audience = audience,
        claims = claims,
    )

fun FellesTestOppsett.skapAzureJwt(
    subject: String = "sykepengesoknad-arkivering-oppgave-client-id",
    navIdent: String = "10987654321",
) = buildAzureClaimSet(
    subject = subject,
    claims = hashMapOf("NAVident" to navIdent),
)

fun MockOAuth2Server.token(
    subject: String,
    issuerId: String = "selvbetjening",
    clientId: String = UUID.randomUUID().toString(),
    audience: String = "loginservice-client-id",
    claims: Map<String, Any> = mapOf("acr" to "Level4"),
): String =
    this
        .issueToken(
            issuerId,
            clientId,
            DefaultOAuth2TokenCallback(
                issuerId = issuerId,
                subject = subject,
                audience = listOf(audience),
                claims = claims,
                expiry = 3600,
            ),
        ).serialize()
