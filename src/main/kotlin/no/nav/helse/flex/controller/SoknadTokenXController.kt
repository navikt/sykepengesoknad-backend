package no.nav.helse.flex.controller

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.config.OIDCIssuer.TOKENX
import no.nav.helse.flex.controller.domain.RSMottakerResponse
import no.nav.helse.flex.controller.domain.RSOppdaterSporsmalResponse
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSMottaker
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.controller.mapper.mapSporsmal
import no.nav.helse.flex.controller.mapper.mapSporsmalTilRs
import no.nav.helse.flex.controller.mapper.mapSvar
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknad
import no.nav.helse.flex.domain.Avsendertype.BRUKER
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.exception.*
import no.nav.helse.flex.logger
import no.nav.helse.flex.sending.SoknadSender
import no.nav.helse.flex.service.AvbrytSoknadService
import no.nav.helse.flex.service.EttersendingSoknadService
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.GjenapneSoknadService
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.service.KorrigerSoknadService
import no.nav.helse.flex.service.MottakerAvSoknadService
import no.nav.helse.flex.service.OppdaterSporsmalService
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import no.nav.helse.flex.svarvalidering.validerSvarPaSoknad
import no.nav.helse.flex.util.Metrikk
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/api/v2"])
class SoknadTokenXController(
    private val contextHolder: TokenValidationContextHolder,
    private val identService: IdentService,
    private val gjenapneSoknadService: GjenapneSoknadService,
    private val soknadSender: SoknadSender,
    private val hentSoknadService: HentSoknadService,
    private val metrikk: Metrikk,
    private val opprettSoknadService: OpprettSoknadService,
    private val korrigerSoknadService: KorrigerSoknadService,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val ettersendingSoknadService: EttersendingSoknadService,
    private val oppdaterSporsmalService: OppdaterSporsmalService,
    private val avbrytSoknadService: AvbrytSoknadService,
    private val environmentToggles: EnvironmentToggles,

    @Value("\${DITT_SYKEFRAVAER_FRONTEND_CLIENT_ID}")
    val dittSykefravaerFrontendClientId: String,

    @Value("\${SYKEPENGESOKNAD_FRONTEND_CLIENT_ID}")
    val sykepengesoknadFrontendClientId: String,

    @Value("\${TOKENX_IDPORTEN_IDP}")
    val tokenxIdportenIdp: String,
) {

    private val log = logger()

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/soknader"], produces = [APPLICATION_JSON_VALUE])
    fun hentSoknader(): List<RSSykepengesoknad> {
        val identer =
            validerTokenXClaims(dittSykefravaerFrontendClientId, sykepengesoknadFrontendClientId).hentIdenter()
        return hentSoknadService.hentSoknader(identer).map { it.tilRSSykepengesoknad() }
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/opprettSoknadUtland"], produces = [APPLICATION_JSON_VALUE])
    fun opprettSoknadUtland(): RSSykepengesoknad {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val identer = validerTokenXClaims(sykepengesoknadFrontendClientId).hentIdenter()
        return opprettSoknadService
            .opprettSoknadUtland(identer)
            .tilRSSykepengesoknad()
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/send"])
    fun sendSoknadRestful(@PathVariable("id") id: String) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknadFraBase, identer) = hentOgSjekkTilgangTilSoknad(id)
        if (soknadFraBase.status == Soknadstatus.SENDT) {
            log.warn("Soknad ${soknadFraBase.id} er allerede sendt")
            return
        }
        soknadFraBase.validerSvarPaSoknad()
        val eldsteSoknaden = hentSoknadService.hentEldsteSoknaden(identer, soknadFraBase.fom)
        if (eldsteSoknaden != null && eldsteSoknaden != soknadFraBase.id) {
            throw ForsokPaSendingAvNyereSoknadException(
                "Forsøk på sending av en nyere søknad med id: ${soknadFraBase.id}."
            )
        }

        try {
            soknadSender.sendSoknad(soknadFraBase, BRUKER, null, identer)
            log.info("Soknad sendt id: ${soknadFraBase.id}")
        } catch (e: Exception) {
            log.error("Innsending av søknad ${soknadFraBase.id} feilet")
            metrikk.tellInnsendingFeilet(soknadFraBase.soknadstype.name)
            throw e
        }
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/ettersendTilNav"])
    fun ettersendTilNav(@PathVariable("id") id: String) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknadFraBase, _) = hentOgSjekkTilgangTilSoknad(id)

        log.info("Ettersender søknad: $id til NAV")
        ettersendingSoknadService.ettersendTilNav(soknadFraBase)
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/ettersendTilArbeidsgiver"])
    fun ettersendTilArbeidsgiver(@PathVariable("id") id: String) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknadFraBase, _) = hentOgSjekkTilgangTilSoknad(id)

        log.info("Ettersender søknad: $id til arbeidsgiver")
        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadFraBase)
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/avbryt"])
    fun avbryt(@PathVariable("id") id: String) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknadFraBase, _) = hentOgSjekkTilgangTilSoknad(id)

        log.info("Avbryter søknad: $id")
        if (Soknadstatus.AVBRUTT == soknadFraBase.status) {
            log.warn("Søknad med id $id er allerede avbrutt")
        } else {
            avbrytSoknadService.avbrytSoknad(soknadFraBase)
        }
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/gjenapne"])
    fun gjenapne(@PathVariable("id") id: String) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknadFraBase, _) = hentOgSjekkTilgangTilSoknad(id)

        return when (soknadFraBase.soknadstype) {
            Soknadstype.SELVSTENDIGE_OG_FRILANSERE,
            Soknadstype.ARBEIDSTAKERE,
            Soknadstype.ANNET_ARBEIDSFORHOLD,
            Soknadstype.ARBEIDSLEDIG,
            Soknadstype.REISETILSKUDD,
            Soknadstype.GRADERT_REISETILSKUDD,
            Soknadstype.BEHANDLINGSDAGER -> {
                if (soknadFraBase.status !== Soknadstatus.AVBRUTT) {
                    log.info("Kan ikke gjenåpne søknad som ikke er avbrutt: $id")
                    throw IllegalArgumentException("Kan ikke gjenåpne søknad som ikke er avbrutt")
                }
                log.info("Gjenåpner søknad: ${soknadFraBase.id}")
                gjenapneSoknadService.gjenapneSoknad(soknadFraBase)
            }

            Soknadstype.OPPHOLD_UTLAND -> log.error("Kallet gjenapneSoknad er ikke støttet for soknadstypen: ${Soknadstype.OPPHOLD_UTLAND}")
        }
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/korriger"])
    fun korriger(@PathVariable("id") id: String): RSSykepengesoknad {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        log.info("Ber om utkast til korrigering av: $id")
        val (soknad, identer) = hentOgSjekkTilgangTilSoknad(id)
        val utkast = korrigerSoknadService.finnEllerOpprettUtkast(soknad, identer)
        return utkast.tilRSSykepengesoknad()
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @PutMapping(
        value = ["/soknader/{soknadId}/sporsmal/{sporsmalId}"],
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun oppdaterSporsmal(
        @PathVariable("soknadId") soknadId: String,
        @PathVariable("sporsmalId") sporsmalId: String,
        @RequestBody rsSporsmal: RSSporsmal
    ): RSOppdaterSporsmalResponse {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val sporsmal = rsSporsmal.mapSporsmal()
        val (soknad, _) = hentOgSjekkTilgangTilSoknad(soknadId)

        if (sporsmalId != sporsmal.id) {
            throw IllegalArgumentException("$sporsmalId != ${sporsmal.id} SporsmalId i body ikke lik sporsmalId i URL ")
        }

        validerStatusOgHovedsporsmal(soknad = soknad, soknadId = soknadId, sporsmalId = sporsmalId)

        val oppdaterSporsmalResultat = oppdaterSporsmalService.oppdaterSporsmal(soknad, sporsmal)
        return skapOppdaterSpmResponse(oppdaterSporsmalResultat, sporsmal.tag)
    }

    private fun skapOppdaterSpmResponse(
        oppdaterSporsmalResultat: OppdaterSporsmalService.OppdaterSporsmalResultat,
        sporsmalTag: String
    ): RSOppdaterSporsmalResponse {
        val sporsmalSomBleOppdatert = oppdaterSporsmalResultat.oppdatertSoknad.sporsmal.find { it.tag == sporsmalTag }!!

        if (oppdaterSporsmalResultat.mutert) {
            return RSOppdaterSporsmalResponse(
                mutertSoknad = oppdaterSporsmalResultat.oppdatertSoknad.tilRSSykepengesoknad(),
                oppdatertSporsmal = sporsmalSomBleOppdatert.mapSporsmalTilRs()
            )
        }
        return RSOppdaterSporsmalResponse(
            mutertSoknad = null,
            oppdatertSporsmal = sporsmalSomBleOppdatert.mapSporsmalTilRs()
        )
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(
        value = ["/soknader/{soknadId}/sporsmal/{sporsmalId}/svar"],
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE]
    )
    fun lagreNyttSvar(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
        @RequestBody svar: RSSvar
    ): RSOppdaterSporsmalResponse {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknad, _) = hentOgSjekkTilgangTilSoknad(soknadId)

        val sporsmal = validerStatusOgHovedsporsmal(soknad = soknad, soknadId = soknadId, sporsmalId = sporsmalId)

        val oppdaterSporsmalResultat = oppdaterSporsmalService.lagreNyttSvar(soknad, sporsmalId, svar.mapSvar())
        return skapOppdaterSpmResponse(oppdaterSporsmalResultat, sporsmal.tag)
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(
        value = ["/soknader/{soknadId}/sporsmal/{sporsmalId}/svar/{svarId}"]
    )
    fun slettSvar(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
        @PathVariable svarId: String,
    ) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknad, _) = hentOgSjekkTilgangTilSoknad(soknadId)
        validerStatusOgHovedsporsmal(soknad = soknad, soknadId = soknadId, sporsmalId = sporsmalId)
        oppdaterSporsmalService.slettSvar(
            lagretSoknad = soknad,
            sporsmalId = sporsmalId,
            svarId = svarId
        )
    }

    private fun validerStatusOgHovedsporsmal(
        soknad: Sykepengesoknad,
        soknadId: String,
        sporsmalId: String
    ): Sporsmal {
        if (!listOf(Soknadstatus.NY, Soknadstatus.UTKAST_TIL_KORRIGERING).contains(soknad.status)) {
            throw FeilStatusForOppdaterSporsmalException("Søknad $soknadId har status ${soknad.status}. Da kan man ikke besvare spørsmål")
        }

        if (!soknad.alleSporsmalOgUndersporsmal().mapNotNull { it.id }.contains(sporsmalId)) {
            throw SporsmalFinnesIkkeISoknadException("$sporsmalId finnes ikke i søknad $soknadId")
        }

        val sporsmal = soknad.sporsmal.find { it.id == sporsmalId }
            ?: throw IllegalArgumentException("$sporsmalId er ikke et hovedspørsmål i søknad $soknadId")
        return sporsmal
    }

    @ProtectedWithClaims(issuer = TOKENX, claimMap = ["acr=Level4"])
    @GetMapping(value = ["/soknader/{id}/mottaker"], produces = [APPLICATION_JSON_VALUE])
    fun hentMottakerAvSoknad(@PathVariable("id") id: String): RSMottakerResponse {
        val (sykepengesoknad, identer) = hentOgSjekkTilgangTilSoknad(id)
        val mottaker = mottakerAvSoknadService.finnMottakerAvSoknad(sykepengesoknad, identer)
        return RSMottakerResponse(RSMottaker.valueOf(mottaker.name))
    }

    data class SoknadOgIdenter(val sykepengesoknad: Sykepengesoknad, val identer: FolkeregisterIdenter)

    private fun hentOgSjekkTilgangTilSoknad(soknadId: String): SoknadOgIdenter {
        val sykepengesoknad = hentSoknadService.finnSykepengesoknad(soknadId)
        val identer = validerTokenXClaims(sykepengesoknadFrontendClientId).hentIdenter()
        if (!identer.alle().contains(sykepengesoknad.fnr)) {
            throw IkkeTilgangException("Er ikke eier")
        }
        return SoknadOgIdenter(sykepengesoknad, identer)
    }

    private fun validerTokenXClaims(vararg tillattClient: String): JwtTokenClaims {
        val context = contextHolder.tokenValidationContext
        val claims = context.getClaims(TOKENX)
        val clientId = claims.getStringClaim("client_id")

        if (!tillattClient.toList().contains(clientId)) {
            throw IkkeTilgangException("Uventet client id $clientId")
        }
        val idp = claims.getStringClaim("idp")
        if (idp != tokenxIdportenIdp) {
            // Sjekker at det var idporten som er IDP for tokenX tokenet
            throw IkkeTilgangException("Uventet idp $idp")
        }
        return claims
    }

    private fun JwtTokenClaims.hentIdenter(): FolkeregisterIdenter {
        return identService.hentFolkeregisterIdenterMedHistorikkForFnr(this.getStringClaim("pid"))
    }
}
