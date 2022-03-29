package no.nav.syfo.controller

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.syfo.config.OIDCIssuer.SELVBETJENING
import no.nav.syfo.controller.domain.soknadarbeidsgiver.RSSoknadArbeidsgiverRespons
import no.nav.syfo.controller.extensions.fnrFraOIDC
import no.nav.syfo.controller.mapper.SoknadArbeidsgiverResponsToRS.mapSoknadArbeidsgiverRespons
import no.nav.syfo.service.SoknadArbeidsgiverService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/api/arbeidsgiver"])
class SoknadArbeidsgiverController(
    private val contextHolder: TokenValidationContextHolder,
    private val soknadArbeidsgiverService: SoknadArbeidsgiverService
) {

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/soknader"], produces = [APPLICATION_JSON_VALUE])
    fun soknaderForNarmesteLeder(
        @RequestParam(required = false)
        orgnummer: String?
    ): RSSoknadArbeidsgiverRespons {
        return mapSoknadArbeidsgiverRespons(soknadArbeidsgiverService.hentSoknader(contextHolder.fnrFraOIDC(), orgnummer))
    }
}
