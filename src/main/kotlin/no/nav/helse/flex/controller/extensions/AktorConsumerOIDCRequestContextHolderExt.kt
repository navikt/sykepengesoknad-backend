package no.nav.helse.flex.controller.extensions

import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.security.token.support.core.context.TokenValidationContextHolder

fun Pair<IdentService, TokenValidationContextHolder>.hentFolkeregisterIdenterMedHistorikk(): FolkeregisterIdenter {
    return this.first.hentFolkeregisterIdenterMedHistorikkForFnr(this.second.fnrFraOIDC())
}
