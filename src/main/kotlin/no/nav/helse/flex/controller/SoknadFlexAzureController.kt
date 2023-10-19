package no.nav.helse.flex.controller

import io.swagger.v3.oas.annotations.Hidden
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknadFlexInternal
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknadFlexInternal
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.KlippetSykepengesoknadDbRecord
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

data class FlexInternalResponse(
    val sykepengesoknadListe: List<RSSykepengesoknadFlexInternal>,
    val klippetSykepengesoknadRecord: Set<KlippetSykepengesoknadDbRecord>
)

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
@RequestMapping(value = ["/api/v1/flex"], produces = [MediaType.APPLICATION_JSON_VALUE])
@Hidden
class SoknadFlexAzureController(
    private val clientIdValidation: ClientIdValidation,
    private val identService: IdentService,
    private val pdlClient: PdlClient,
    private val hentSoknadService: HentSoknadService,
    private val klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository
) {
    val log = logger()

    @RequestMapping(method = [RequestMethod.GET], path = ["/sykepengesoknader"])
    fun hentSykepengeSoknader(@RequestHeader(value = "fnr") fnr: String): FlexInternalResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val soknader = hentSoknadService
            .hentSoknader(identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr))
            .map { it.tilRSSykepengesoknadFlexInternal() }

        val sokUuider = soknader.map { it.id }
        val klippetSoknad = klippetSykepengesoknadRepository.findAllBySykepengesoknadUuidIn(sokUuider)

        val sykUuider = soknader.filter { it.sykmeldingId != null }.map { it.sykmeldingId!! }
        val klippetSykmelding = klippetSykepengesoknadRepository.findAllBySykmeldingUuidIn(sykUuider)

        return FlexInternalResponse(
            sykepengesoknadListe = soknader,
            klippetSykepengesoknadRecord = (klippetSoknad + klippetSykmelding).toSet()
        )
    }

    @RequestMapping(method = [RequestMethod.GET], path = ["/identer"])
    fun hentIdenter(@RequestHeader(value = "ident") ident: String): List<PdlIdent> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return pdlClient.hentIdenterMedHistorikk(ident)
    }
}
