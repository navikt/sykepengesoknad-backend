package no.nav.helse.flex.controller

import io.swagger.v3.oas.annotations.Hidden
import jakarta.servlet.http.HttpServletRequest
import no.nav.helse.flex.client.aareg.AaregClient
import no.nav.helse.flex.client.aareg.Arbeidsforhold
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerregisterClient
import no.nav.helse.flex.client.pdl.PdlClient
import no.nav.helse.flex.client.pdl.PdlIdent
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjongivendeInntektClient
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.NamespaceAndApp
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknadFlexInternal
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknad
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknadFlexInternal
import no.nav.helse.flex.domain.AuditEntry
import no.nav.helse.flex.domain.EventType
import no.nav.helse.flex.frisktilarbeid.*
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.repository.KlippetSykepengesoknadDbRecord
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.util.UUID

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
    private val aaregClient: AaregClient,
    private val pensjongivendeInntektClient: PensjongivendeInntektClient,
    private val identService: IdentService,
    private val friskTilArbeidService: FriskTilArbeidService,
    private val pdlClient: PdlClient,
    private val hentSoknadService: HentSoknadService,
    private val klippetSykepengesoknadRepository: KlippetSykepengesoknadRepository,
    private val friskTilArbeidRepository: FriskTilArbeidRepository,
    private val auditLogProducer: AuditLogProducer,
    private val arbeidssokerregisterClient: ArbeidssokerregisterClient,
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
        request: HttpServletRequest,
    ): FlexInternalResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()
        val soknader =
            hentSoknadService
                .hentSoknader(identService.hentFolkeregisterIdenterMedHistorikkForFnr(req.fnr))
                .map { it.tilRSSykepengesoknadFlexInternal() }

        val sokUuider = soknader.map { it.id }
        val klippetSoknad = klippetSykepengesoknadRepository.findAllBySykepengesoknadUuidIn(sokUuider)

        val sykUuider = soknader.filter { it.sykmeldingId != null }.map { it.sykmeldingId!! }
        val klippetSykmelding = klippetSykepengesoknadRepository.findAllBySykmeldingUuidIn(sykUuider)

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = req.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter alle sykepengesoknader",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

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
        request: HttpServletRequest,
    ): List<PdlIdent> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = req.ident,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter alle identer for ident",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return pdlClient.hentIdenterMedHistorikk(req.ident)
    }

    @GetMapping("/api/v1/flex/sykepengesoknader/{id}")
    fun hentSykepengesoknad(
        @PathVariable id: String,
    ): FlexInternalSoknadResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()
        val soknad = hentSoknadService.finnSykepengesoknad(id)

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = soknad.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter en sykepengesoknad",
                requestUrl = URI.create("/api/v1/flex/sykepengesoknader/${soknad.id}"),
                requestMethod = "GET",
            ),
        )

        return FlexInternalSoknadResponse(
            sykepengesoknad = soknad.tilRSSykepengesoknad(),
            fnr = soknad.fnr,
            arbeidsforholdFraAareg = soknad.arbeidsforholdFraAareg,
        )
    }

    data class HentAaregdataRequest(
        val fnr: String,
    )

    @PostMapping("/api/v1/flex/aareg", consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun hentAaregdata(
        @RequestBody req: HentAaregdataRequest,
        request: HttpServletRequest,
    ): List<Arbeidsforhold> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = req.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter aareg data",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return aaregClient.hentArbeidsforhold(req.fnr)
    }

    data class HentPensjonsgivendeInntektRequest(
        val fnr: String,
        val inntektsaar: String,
    )

    @PostMapping("/api/v1/flex/sigrun", consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun hentPensjonsgivendeInntekt(
        @RequestBody req: HentPensjonsgivendeInntektRequest,
        request: HttpServletRequest,
    ): HentPensjonsgivendeInntektResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = req.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter pensjonsgivende inntekt",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return pensjongivendeInntektClient.hentPensjonsgivendeInntekt(req.fnr, req.inntektsaar.toInt())
    }

    data class FnrRequest(
        val fnr: String,
    )

    @PostMapping(
        "/api/v1/flex/arbeidssokerregister",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentSisteArbeidssokerperiode(
        @RequestBody req: FnrRequest,
        request: HttpServletRequest,
    ): List<ArbeidssokerperiodeResponse> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = req.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter arbeidssøkerregister status",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return arbeidssokerregisterClient.hentSisteArbeidssokerperiode(ArbeidssokerperiodeRequest(req.fnr))
    }

    @PostMapping(
        "/api/v1/flex/fta-vedtak-for-person",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentFriskmeldtVedtak(
        @RequestBody req: FnrRequest,
        request: HttpServletRequest,
    ): List<FriskTilArbeidVedtakDbRecord> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = req.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter friskmeldt til arbeidsformidling vedtak",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return friskTilArbeidRepository.findByFnrIn(
            identService.hentFolkeregisterIdenterMedHistorikkForFnr(req.fnr).alle(),
        )
    }

    data class OpprettRequest(
        val fnr: String,
        val fom: LocalDate,
        val tom: LocalDate,
    )

    @PostMapping(
        "/api/v1/flex/fta-vedtak-for-person/opprett",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun opprettFriskmeldtVedtak(
        @RequestBody req: OpprettRequest,
        request: HttpServletRequest,
    ): FriskTilArbeidVedtakStatusKafkaMelding {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        if (req.fom.isAfter(req.tom)) {
            throw IllegalArgumentException("Fom kan ikke være etter tom")
        }
        // Kast feil hvis mer enn 15 uker
        if (req.fom.plusWeeks(15) < req.tom) {
            throw IllegalArgumentException("Kan ikke være mer enn 15 uker")
        }

        val melding =
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = UUID.randomUUID().toString(),
                friskTilArbeidVedtakStatus =
                    FriskTilArbeidVedtakStatus(
                        personident = req.fnr,
                        fom = req.fom,
                        tom = req.tom,
                        begrunnelse = "flex-internal-frontend manuelt opprettet",
                        statusAt = OffsetDateTime.now(),
                        status = Status.FATTET,
                        uuid = UUID.randomUUID().toString(),
                        statusBy = navIdent,
                    ),
            )

        friskTilArbeidService.lagreFriskTilArbeidVedtakStatus(melding)

        return melding
    }

    @GetMapping(
        "/api/v1/flex/fta-vedtak/ubehandlede",
        produces = [APPLICATION_JSON_VALUE],
    )
    fun ubehandledeFtaVedtak(): List<FriskTilArbeidVedtakDbRecord> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))

        return friskTilArbeidRepository.findAll()
            .filter {
                it.behandletStatus !in
                    listOf(
                        BehandletStatus.NY,
                        BehandletStatus.BEHANDLET,
                        BehandletStatus.OVERLAPP_OK,
                    )
            }.sortedBy { it.opprettet }
    }

    data class EndreStatusRequest(
        val id: String,
        val status: BehandletStatus,
    )

    @PostMapping(
        "/api/v1/flex/fta-vedtak/endre-status",
        produces = [APPLICATION_JSON_VALUE],
        consumes = [APPLICATION_JSON_VALUE],
    )
    fun endreStatus(
        @RequestBody req: EndreStatusRequest,
    ): FriskTilArbeidVedtakDbRecord {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))

        val eksisterende =
            friskTilArbeidRepository.findById(req.id).orElseThrow { IllegalArgumentException("Fant ikke vedtak") }

        if (req.status !in listOf(BehandletStatus.NY, BehandletStatus.OVERLAPP_OK)) {
            throw IllegalArgumentException("Kan ikke endre status til ${req.status}")
        }
        if (req.status == BehandletStatus.OVERLAPP_OK && eksisterende.behandletStatus != BehandletStatus.OVERLAPP) {
            throw IllegalArgumentException("Kan ikke endre status til OVERLAPP_OK på vedtak som ikke er overlapp")
        }
        if (req.status == BehandletStatus.NY && eksisterende.behandletStatus !in
            listOf(
                BehandletStatus.SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET,
                BehandletStatus.INGEN_ARBEIDSSOKERPERIODE,
            )
        ) {
            throw IllegalArgumentException(
                "Kan ikke endre status til NY på vedtak som ikke SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET eller INGEN_ARBEIDSSOKERPERIODE",
            )
        }
        val ny = eksisterende.copy(behandletStatus = req.status)
        return friskTilArbeidRepository.save(ny)
    }
}
