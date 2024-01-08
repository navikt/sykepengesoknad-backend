package no.nav.helse.flex.controller

import io.swagger.v3.oas.annotations.Hidden
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknadFlexInternal
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknadFlexInternal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.kafka.producer.SoknadProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.KlippetSykepengesoknadDbRecord
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.repository.SykepengesoknadRepository
import no.nav.helse.flex.repository.normaliser
import no.nav.helse.flex.sending.finnOpprinneligSendt
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.*

data class FlexInternalResponse(
    val sykepengesoknadListe: List<RSSykepengesoknadFlexInternal>,
    val klippetSykepengesoknadRecord: Set<KlippetSykepengesoknadDbRecord>,
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
    private val klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository,
    private val soknadProducer: SoknadProducer,
    private val sykepengesoknadRepository: SykepengesoknadRepository,
    private val sykepengesoknadDAO: SykepengesoknadDAO,
) {
    private val log = logger()

    @GetMapping("/sykepengesoknader")
    fun hentSykepengeSoknader(
        @RequestHeader fnr: String,
    ): FlexInternalResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val soknader =
            hentSoknadService
                .hentSoknader(identService.hentFolkeregisterIdenterMedHistorikkForFnr(fnr))
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

    @GetMapping("/identer")
    fun hentIdenter(
        @RequestHeader ident: String,
    ): List<PdlIdent> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        return pdlClient.hentIdenterMedHistorikk(ident)
    }

    @PostMapping("/republiser/{id}")
    fun republiserSoknad(
        @PathVariable id: String,
    ) {
        // TODO: Lag en extension-funksjon som sjekker om UUID er gyldig.
        val sykepengesoknadUuid = UUID.fromString(id).toString()
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        hentSoknadService.finnSykepengesoknad(sykepengesoknadUuid).let {
            soknadProducer.soknadEvent(
                sykepengesoknad = it,
                mottaker = sykepengesoknadDAO.finnMottakerAvSoknad(it.id),
                erEttersending = false,
                dodsdato = null,
                opprinneligSendt = finnOpprinneligSendt(it),
            )

            log.info("Republiserte søknad med sykpengesoknad_uuid: $sykepengesoknadUuid")
        }
    }

    private fun finnOpprinneligSendt(it: Sykepengesoknad): Instant? {
        return sykepengesoknadRepository.findByFnrIn(pdlClient.hentIdenterMedHistorikk(it.fnr).map { it.ident })
            .finnOpprinneligSendt(it.normaliser().soknad)
    }
}
