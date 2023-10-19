package no.nav.helse.flex.controller

import io.swagger.v3.oas.annotations.Hidden
import no.nav.helse.flex.client.syfotilgangskontroll.SyfoTilgangskontrollClient
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
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
@RequestMapping(value = ["/api/veileder/soknader"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Hidden
class SoknadVeilederAzureController(
    private val clientIdValidation: ClientIdValidation,
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollClient,
    private val identService: IdentService,
    private val hentSoknadService: HentSoknadService
) {
    val log = logger()

    @RequestMapping(method = [RequestMethod.GET])
    fun hentVeilederSoknader(
        @RequestHeader(value = "nav-personident") fnr: String
    ): List<RSSykepengesoknad> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "teamsykefravr", app = "syfomodiaperson"))

        if (!syfoTilgangskontrollClient.sjekkTilgangVeileder(fnr)) {
            log.info("Veileder forsøker å hente søknader, men har ikke tilgang til bruker.")
            throw IkkeTilgangException("Har ikke tilgang til bruker")
        }
        return hentSoknadService
            .hentSoknader(identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr))
            .map { it.tilRSSykepengesoknad() }
    }
}
