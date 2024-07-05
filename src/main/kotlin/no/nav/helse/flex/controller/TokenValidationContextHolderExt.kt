package no.nav.helse.flex.controller

import no.nav.helse.flex.config.OIDCIssuer.TOKENX
import no.nav.helse.flex.exception.IkkeTilgangException
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtTokenClaims

fun TokenValidationContextHolder.validerTokenXClaims(vararg tillattClient: String): JwtTokenClaims {
    val context = this.getTokenValidationContext()
    val claims = context.getClaims(TOKENX)
    val clientId = claims.getStringClaim("client_id")

    if (!tillattClient.toList().contains(clientId)) {
        throw IkkeTilgangException("Uventet clientId: $clientId")
    }
    return claims
}
