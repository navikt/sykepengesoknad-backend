package no.nav.helse.flex.controller

import io.swagger.v3.oas.annotations.Hidden
import no.nav.helse.flex.client.istilgangskontroll.IstilgangskontrollClient
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknad
import no.nav.helse.flex.exception.IkkeTilgangException
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
@Hidden
class SoknadVeilederAzureController(
    private val clientIdValidation: ClientIdValidation,
    private val istilgangskontrollClient: IstilgangskontrollClient,
    private val identService: IdentService,
    private val hentSoknadService: HentSoknadService,
) {
    val log = logger()

    @GetMapping(value = ["/api/veileder/soknader"], produces = [APPLICATION_JSON_VALUE])
    fun deprecatedHentVeilederSoknader(
        @RequestHeader(value = "nav-personident") fnr: String,
    ): List<RSSykepengesoknad> {
        return hentSoknader(fnr)
    }

    data class HentVeilederSoknaderRequest(
        val fnr: String,
    )

    @PostMapping(
        value = ["/api/veileder/soknader"],
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentVeilederSoknader(
        @RequestBody req: HentVeilederSoknaderRequest,
    ): List<RSSykepengesoknad> {
        return hentSoknader(req.fnr)
    }

    private fun hentSoknader(fnr: String): List<RSSykepengesoknad> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "teamsykefravr", app = "syfomodiaperson"))

        if (!istilgangskontrollClient.sjekkTilgangVeileder(fnr)) {
            log.info("Veileder forsøker å hente søknader, men har ikke tilgang til bruker.")
            throw IkkeTilgangException("Har ikke tilgang til bruker")
        }
        return hentSoknadService
            .hentSoknader(identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr))
            .map { it.tilRSSykepengesoknad() }
    }
}
