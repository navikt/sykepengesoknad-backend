package no.nav.helse.flex.controller.extensions

import no.nav.helse.flex.config.OIDCIssuer.SELVBETJENING
import no.nav.security.token.support.core.context.TokenValidationContextHolder

fun TokenValidationContextHolder.fnrFraOIDC(): String {
    val claims = this.tokenValidationContext.getClaims(SELVBETJENING)
    return claims.getStringClaim("pid") ?: claims.subject
}
