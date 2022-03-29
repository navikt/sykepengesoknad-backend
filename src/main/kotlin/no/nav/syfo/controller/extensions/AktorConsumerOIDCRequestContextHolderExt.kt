package no.nav.syfo.controller.extensions

import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.syfo.service.FolkeregisterIdenter
import no.nav.syfo.service.IdentService

fun Pair<IdentService, TokenValidationContextHolder>.hentFolkeregisterIdenterMedHistorikk(): FolkeregisterIdenter {
    return this.first.hentFolkeregisterIdenterMedHistorikkForFnr(this.second.fnrFraOIDC())
}
