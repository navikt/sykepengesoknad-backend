package no.nav.helse.flex.clientidvalidation

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.exception.AbstractApiError
import no.nav.helse.flex.exception.LogLevel
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component

private const val AZP_CLAIM = "azp"

@Component
class ClientIdValidation(
    private val tokenValidationContextHolder: TokenValidationContextHolder,
    @Value("\${AZURE_APP_PRE_AUTHORIZED_APPS}") private val azureAppPreAuthorizedApps: String,
) {
    private val log = logger()
    private val allowedClientIds: List<PreAuthorizedClient> = OBJECT_MAPPER.readValue(azureAppPreAuthorizedApps)

    data class NamespaceAndApp(val namespace: String, val app: String)

    fun validateClientId(app: NamespaceAndApp) = validateClientId(listOf(app))

    fun validateClientId(apps: List<NamespaceAndApp>) {
        val clientIds =
            allowedClientIds
                .filter { apps.contains(it.tilNamespaceAndApp()) }
                .map { it.clientId }

        val azp = tokenValidationContextHolder.hentAzpClaim()
        if (clientIds.ikkeInneholder(azp)) {
            throw UkjentClientException("Ukjent client")
        }
    }

    private fun TokenValidationContextHolder.hentAzpClaim(): String {
        return this.getTokenValidationContext().getJwtToken(AZUREATOR)
            ?.jwtTokenClaims
            ?.getStringClaim(AZP_CLAIM)
            ?: throw UkjentClientException("Fant ikke azp claim.")
    }

    private fun List<String>.ikkeInneholder(s: String): Boolean {
        return !this.contains(s)
    }
}

class UkjentClientException(message: String, grunn: Throwable? = null) : AbstractApiError(
    message = message,
    httpStatus = HttpStatus.FORBIDDEN,
    reason = "UKJENT_CLIENT",
    loglevel = LogLevel.WARN,
    grunn = grunn,
)

private fun PreAuthorizedClient.tilNamespaceAndApp(): NamespaceAndApp {
    val splitt = name.split(":")
    return NamespaceAndApp(namespace = splitt[1], app = splitt[2])
}

data class PreAuthorizedClient(val name: String, val clientId: String)
