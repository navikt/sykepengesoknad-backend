package no.nav.helse.flex.controller

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.config.OIDCIssuer.TOKENX
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknad
import no.nav.helse.flex.exception.AbstractApiError
import no.nav.helse.flex.exception.ReadOnlyException
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping(value = ["/api/v1/arbeidsgiver"])
class ArbeidsgiverInntektsmeldingController(
    private val contextHolder: TokenValidationContextHolder,
    private val identService: IdentService,
    private val hentSoknadService: HentSoknadService,

    private val environmentToggles: EnvironmentToggles,

    @Value("\${SPINNTEKTSMELDING_FRONTEND_CLIENT_ID}")
    val spinntektsmeldingFrontendClientId: String,

    ) {
    private val log = logger()

    data class HentSoknaderRquest(
        val fnr: String,
        val eldsteFom: LocalDate,
    )

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @PostMapping(
        value = ["/soknader"], produces = [APPLICATION_JSON_VALUE],
        consumes = [APPLICATION_JSON_VALUE]
    )
    fun hentSoknaderForInntektsmeldingFrontend(
        @RequestBody request: HentSoknaderRquest,
    ): RSSykepengesoknad {
        if (environmentToggles.isProduction()) {
            throw AbstractApiError(ReadOnlyException("Kan ikke opprette søknad i produksjon"))
        }
        contextHolder.validerTokenXClaims(spinntektsmeldingFrontendClientId)

        return opprettSoknadService
            .opprettSoknadUtland(identer)
            .tilRSSykepengesoknad()
    }

}
