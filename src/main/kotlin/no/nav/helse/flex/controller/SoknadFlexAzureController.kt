package no.nav.helse.flex.controller

import io.swagger.v3.oas.annotations.Hidden
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknadFlexInternal
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknad
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknadFlexInternal
import no.nav.helse.flex.repository.KlippetSykepengesoknadDbRecord
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.ArbeidsforholdFraAAreg
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*

data class FlexInternalResponse(
    val sykepengesoknadListe: List<RSSykepengesoknadFlexInternal>,
    val klippetSykepengesoknadRecord: Set<KlippetSykepengesoknadDbRecord>,
)

data class FlexInternalSoknadResponse(
    val sykepengesoknad: RSSykepengesoknad,
    val fnr: String,
    val arbeidsforholdFraAareg: List<ArbeidsforholdFraAAreg>?,
)

@RestController
@ProtectedWithClaims(issuer = AZUREATOR)
@Hidden
class SoknadFlexAzureController(
    private val clientIdValidation: ClientIdValidation,
    private val identService: IdentService,
    private val pdlClient: PdlClient,
    private val hentSoknadService: HentSoknadService,
    private val klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository,
) {
    data class HentSykepengesoknaderRequest(
        val fnr: String,
    )

    @PostMapping(
        "/api/v1/flex/sykepengesoknader",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentSykepengesoknader(
        @RequestBody req: HentSykepengesoknaderRequest,
    ): FlexInternalResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val soknader =
            hentSoknadService
                .hentSoknader(identService.hentFolkeregisterIdenterMedHistorikkForFnr(req.fnr))
                .map { it.tilRSSykepengesoknadFlexInternal() }

        val sokUuider = soknader.map { it.id }
        val klippetSoknad = klippetSykepengesoknadRepository.findAllBySykepengesoknadUuidIn(sokUuider)

        val sykUuider = soknader.filter { it.sykmeldingId != null }.map { it.sykmeldingId!! }
        val klippetSykmelding = klippetSykepengesoknadRepository.findAllBySykmeldingUuidIn(sykUuider)

        return FlexInternalResponse(
            sykepengesoknadListe = soknader,
            klippetSykepengesoknadRecord = (klippetSoknad + klippetSykmelding).toSet(),
        )
    }

    data class HentIdenterRequest(
        val ident: String,
    )

    @PostMapping("/api/v1/flex/identer", consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun hentIdenter(
        @RequestBody req: HentIdenterRequest,
    ): List<PdlIdent> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return pdlClient.hentIdenterMedHistorikk(req.ident)
    }

    @GetMapping("/api/v1/flex/sykepengesoknader/{id}")
    fun hentSykepengesoknad(
        @PathVariable id: String,
    ): FlexInternalSoknadResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val soknad = hentSoknadService.finnSykepengesoknad(id)
        return FlexInternalSoknadResponse(
            sykepengesoknad = soknad.tilRSSykepengesoknad(),
            fnr = soknad.fnr,
            arbeidsforholdFraAareg = soknad.arbeidsforholdFraAareg,
        )
    }
}
