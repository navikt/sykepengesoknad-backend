package no.nav.syfo.controller.extensions

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.syfo.config.OIDCIssuer.SELVBETJENING

fun TokenValidationContextHolder.fnrFraOIDC(): String {
    val claims = this.tokenValidationContext.getClaims(SELVBETJENING)
    return claims.getStringClaim("pid") ?: claims.subject
}
