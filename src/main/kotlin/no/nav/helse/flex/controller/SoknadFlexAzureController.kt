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
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.frisktilarbeid.*
import no.nav.helse.flex.kafka.producer.AuditLogProducer
import no.nav.helse.flex.logger
import no.nav.helse.flex.repository.KlippetSykepengesoknadDbRecord
import no.nav.helse.flex.repository.KlippetSykepengesoknadRepository
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.soknadsopprettelse.aaregdata.ArbeidsforholdFraAAreg
import no.nav.helse.flex.util.tilOsloInstant
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.http.ResponseEntity
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
    private val sykepengesoknadDAO: SykepengesoknadDAO,
) {
    val log = logger()

    data class HentSykepengesoknaderRequest(
        val fnr: String,
    )

    @PostMapping(
        "/api/v1/flex/sykepengesoknader",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentSykepengesoknader(
        request: HttpServletRequest,
        @RequestBody requestBody: HentSykepengesoknaderRequest,
    ): FlexInternalResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()
        val soknader =
            hentSoknadService
                .hentSoknader(identService.hentFolkeregisterIdenterMedHistorikkForFnr(requestBody.fnr))
                .map { it.tilRSSykepengesoknadFlexInternal() }

        val sokUuider = soknader.map { it.id }
        val klippetSoknad = klippetSykepengesoknadRepository.findAllBySykepengesoknadUuidIn(sokUuider)

        val sykUuider = soknader.filter { it.sykmeldingId != null }.map { it.sykmeldingId!! }
        val klippetSykmelding = klippetSykepengesoknadRepository.findAllBySykmeldingUuidIn(sykUuider)

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = requestBody.fnr,
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
        request: HttpServletRequest,
        @RequestBody requestBody: HentIdenterRequest,
    ): List<PdlIdent> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = requestBody.ident,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter alle identer for ident",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return pdlClient.hentIdenterMedHistorikk(requestBody.ident)
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
        request: HttpServletRequest,
        @RequestBody requestBody: HentAaregdataRequest,
    ): List<Arbeidsforhold> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = requestBody.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter aareg data",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return aaregClient.hentArbeidsforhold(requestBody.fnr)
    }

    data class HentPensjonsgivendeInntektRequest(
        val fnr: String,
        val inntektsaar: String,
    )

    @PostMapping("/api/v1/flex/sigrun", consumes = [APPLICATION_JSON_VALUE], produces = [APPLICATION_JSON_VALUE])
    fun hentPensjonsgivendeInntekt(
        request: HttpServletRequest,
        @RequestBody requestBody: HentPensjonsgivendeInntektRequest,
    ): HentPensjonsgivendeInntektResponse {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = requestBody.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter pensjonsgivende inntekt",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return pensjongivendeInntektClient.hentPensjonsgivendeInntekt(requestBody.fnr, requestBody.inntektsaar.toInt())
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
        request: HttpServletRequest,
        @RequestBody requestBody: FnrRequest,
    ): List<ArbeidssokerperiodeResponse> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = requestBody.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter arbeidssøkerregister status",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return arbeidssokerregisterClient.hentSisteArbeidssokerperiode(
            ArbeidssokerperiodeRequest(
                requestBody.fnr,
                siste = false,
            ),
        )
    }

    @PostMapping(
        "/api/v1/flex/fta-vedtak-for-person",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentFriskmeldtVedtak(
        request: HttpServletRequest,
        @RequestBody requestBody: FnrRequest,
    ): List<FriskTilArbeidVedtakDbRecord> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        auditLogProducer.lagAuditLog(
            AuditEntry(
                appNavn = "flex-internal",
                utførtAv = navIdent,
                oppslagPå = requestBody.fnr,
                eventType = EventType.READ,
                forespørselTillatt = true,
                oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                beskrivelse = "Henter friskmeldt til arbeidsformidling vedtak",
                requestUrl = URI.create(request.requestURL.toString()),
                requestMethod = "POST",
            ),
        )

        return friskTilArbeidRepository.findByFnrIn(
            identService.hentFolkeregisterIdenterMedHistorikkForFnr(requestBody.fnr).alle(),
        )
    }

    data class OpprettRequest(
        val fnr: String,
        val fom: LocalDate,
        val tom: LocalDate,
        val ignorerArbeidssokerregister: Boolean,
    )

    @PostMapping(
        "/api/v1/flex/fta-vedtak-for-person/opprett",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun opprettFriskmeldtVedtak(
        @RequestBody requestBody: OpprettRequest,
    ): FriskTilArbeidVedtakStatusKafkaMelding {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        if (requestBody.fom.isAfter(requestBody.tom)) {
            throw IllegalArgumentException("Fom kan ikke være etter tom")
        }
        // Kast feil hvis mer enn 15 uker
        if (requestBody.fom.plusWeeks(15) < requestBody.tom) {
            throw IllegalArgumentException("Kan ikke være mer enn 15 uker")
        }

        val melding =
            FriskTilArbeidVedtakStatusKafkaMelding(
                key = UUID.randomUUID().toString(),
                friskTilArbeidVedtakStatus =
                    FriskTilArbeidVedtakStatus(
                        personident = requestBody.fnr,
                        fom = requestBody.fom,
                        tom = requestBody.tom,
                        begrunnelse = "flex-internal-frontend manuelt opprettet",
                        statusAt = OffsetDateTime.now(),
                        status = Status.FATTET,
                        uuid = UUID.randomUUID().toString(),
                        statusBy = navIdent,
                    ),
                ignorerArbeidssokerregister = requestBody.ignorerArbeidssokerregister,
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

        return friskTilArbeidRepository
            .findAll()
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
        @RequestBody requestBody: EndreStatusRequest,
    ): FriskTilArbeidVedtakDbRecord {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))

        val eksisterende =
            friskTilArbeidRepository
                .findById(requestBody.id)
                .orElseThrow { IllegalArgumentException("Fant ikke vedtak") }

        if (requestBody.status !in listOf(BehandletStatus.NY, BehandletStatus.OVERLAPP_OK)) {
            throw IllegalArgumentException("Kan ikke endre status til ${requestBody.status}")
        }
        if (requestBody.status == BehandletStatus.OVERLAPP_OK && eksisterende.behandletStatus != BehandletStatus.OVERLAPP) {
            throw IllegalArgumentException("Kan ikke endre status til OVERLAPP_OK på vedtak som ikke er overlapp")
        }
        if (requestBody.status == BehandletStatus.NY &&
            eksisterende.behandletStatus !in
            listOf(
                BehandletStatus.SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET,
                BehandletStatus.INGEN_ARBEIDSSOKERPERIODE,
                BehandletStatus.BEHANDLET,
                BehandletStatus.OVERLAPP,
            )
        ) {
            throw IllegalArgumentException(
                "Kan ikke endre status til NY på vedtak som ikke SISTE_ARBEIDSSOKERPERIODE_AVSLUTTET, " +
                    "INGEN_ARBEIDSSOKERPERIODE eller BEHANDLET",
            )
        }
        val ny = eksisterende.copy(behandletStatus = requestBody.status)
        return friskTilArbeidRepository.save(ny)
    }

    data class IgnorerArbeidssokerregisterRequest(
        val id: String,
        val ignorerArbeidssokerregister: Boolean,
    )

    @PostMapping(
        "/api/v1/flex/fta-vedtak/ignorer-arbs",
        produces = [APPLICATION_JSON_VALUE],
        consumes = [APPLICATION_JSON_VALUE],
    )
    fun ignorerArbs(
        @RequestBody requestBody: IgnorerArbeidssokerregisterRequest,
    ): FriskTilArbeidVedtakDbRecord {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))

        val eksisterende =
            friskTilArbeidRepository
                .findById(requestBody.id)
                .orElseThrow { IllegalArgumentException("Fant ikke vedtak") }

        val ny =
            eksisterende.copy(
                ignorerArbeidssokerregister = requestBody.ignorerArbeidssokerregister,
            )
        return friskTilArbeidRepository.save(ny)
    }

    data class EndreTomRequest(
        val id: String,
        val tom: LocalDate,
    )

    @PostMapping(
        "/api/v1/flex/fta-vedtak/endre-tom",
        produces = [APPLICATION_JSON_VALUE],
        consumes = [APPLICATION_JSON_VALUE],
    )
    fun endreStatus(
        @RequestBody requestBody: EndreTomRequest,
    ): FriskTilArbeidVedtakDbRecord {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))

        val eksisterende =
            friskTilArbeidRepository
                .findById(requestBody.id)
                .orElseThrow { IllegalArgumentException("Fant ikke vedtak") }

        if (eksisterende.behandletStatus != BehandletStatus.BEHANDLET) {
            throw IllegalArgumentException("Kan ikke endre tom på vedtak som ikke er behandlet")
        }
        log.info("Endrer tom fra ${eksisterende.tom} til ${requestBody.tom} for vedtak ${eksisterende.id}")

        val ny = eksisterende.copy(tom = requestBody.tom)
        return friskTilArbeidRepository.save(ny)
    }

    data class SlettSykepengesoknadRequest(
        val fnr: String,
        val sykepengesoknadId: String,
    )

    @DeleteMapping(
        "/api/v1/flex/fta-soknad",
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun slettSykepengesoknad(
        request: HttpServletRequest,
        @RequestBody requestBody: SlettSykepengesoknadRequest,
    ): ResponseEntity<Any> {
        clientIdValidation.validateClientId(NamespaceAndApp(namespace = "flex", app = "flex-internal-frontend"))
        val navIdent = clientIdValidation.hentNavIdent()

        try {
            val soknader =
                hentSoknadService.hentSoknader(identService.hentFolkeregisterIdenterMedHistorikkForFnr(requestBody.fnr))
            val soknad =
                soknader.find { it.id == requestBody.sykepengesoknadId }
                    ?: return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body("Fant ikke søknad med id: ${requestBody.sykepengesoknadId} tilhørende den aktuelle brukeren.")

            if (soknad.soknadstype != Soknadstype.FRISKMELDT_TIL_ARBEIDSFORMIDLING) {
                val error = "Kan kun slette søknadstype ${soknad.soknadstype} FRISKMELDT_TIL_ARBEIDSFORMIDLING."
                log.error(error)
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
            }

            if (soknad.status !in listOf(Soknadstatus.NY, Soknadstatus.FREMTIDIG)) {
                val error = "Kan kun slette søknader med status NY eller FREMTIDIG."
                log.error(error)
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error)
            }

            sykepengesoknadDAO.slettSoknad(soknad)

            auditLogProducer.lagAuditLog(
                AuditEntry(
                    appNavn = "flex-internal",
                    utførtAv = navIdent,
                    oppslagPå = soknad.fnr,
                    eventType = EventType.DELETE,
                    forespørselTillatt = true,
                    oppslagUtførtTid = LocalDateTime.now().tilOsloInstant(),
                    beskrivelse = "Sletter sykepengesoknad av type FRISKMELDT_TIL_ARBEIDSFORMIDLING med id: ${soknad.id}.",
                    requestUrl = URI.create(request.requestURL.toString()),
                    requestMethod = "DELETE",
                ),
            )

            return ResponseEntity.ok().build()
        } catch (e: Exception) {
            log.error("Feil ved sletting av søknad:", e)
            return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Feil ved sletting av søknad.")
        }
    }
}
