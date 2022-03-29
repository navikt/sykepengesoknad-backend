package no.nav.syfo.controller

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.syfo.client.syfotilgangskontroll.SyfoTilgangskontrollClient
import no.nav.syfo.clientidvalidation.ClientIdValidation
import no.nav.syfo.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.syfo.config.OIDCIssuer.AZUREATOR
import no.nav.syfo.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.syfo.controller.mapper.tilRSSykepengesoknad
import no.nav.syfo.exception.IkkeTilgangException
import no.nav.syfo.logger
import no.nav.syfo.service.HentSoknadService
import no.nav.syfo.service.IdentService
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
@RequestMapping(value = ["/syfosoknad/api/veileder/soknader"], produces = [MediaType.APPLICATION_JSON_VALUE])
class SoknadVeilederAzureController(
    private val clientIdValidation: ClientIdValidation,
    private val syfoTilgangskontrollClient: SyfoTilgangskontrollClient,
    private val identService: IdentService,
    private val hentSoknadService: HentSoknadService
) {
    val log = logger()

    @RequestMapping(method = [RequestMethod.GET])
    fun hentVeilederSoknader(@RequestParam(value = "fnr") fnr: String): List<RSSykepengesoknad> {

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
