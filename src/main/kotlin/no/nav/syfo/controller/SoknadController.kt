package no.nav.syfo.controller

import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.syfo.config.OIDCIssuer.SELVBETJENING
import no.nav.syfo.controller.domain.RSMottakerResponse
import no.nav.syfo.controller.domain.RSOppdaterSporsmalResponse
import no.nav.syfo.controller.domain.sykepengesoknad.RSMottaker
import no.nav.syfo.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.syfo.controller.domain.sykepengesoknad.RSSvar
import no.nav.syfo.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.syfo.controller.extensions.hentFolkeregisterIdenterMedHistorikk
import no.nav.syfo.controller.mapper.*
import no.nav.syfo.domain.Avsendertype.BRUKER
import no.nav.syfo.domain.Soknadstatus
import no.nav.syfo.domain.Soknadstype
import no.nav.syfo.domain.Sporsmal
import no.nav.syfo.domain.Sykepengesoknad
import no.nav.syfo.exception.FeilStatusForOppdaterSporsmalException
import no.nav.syfo.exception.IkkeTilgangException
import no.nav.syfo.exception.SporsmalFinnesIkkeISoknadException
import no.nav.syfo.logger
import no.nav.syfo.service.*
import no.nav.syfo.soknadsopprettelse.OpprettSoknadService
import no.nav.syfo.svarvalidering.validerSvarPaSoknad
import no.nav.syfo.util.Metrikk
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/api"])
class SoknadController(
    private val contextHolder: TokenValidationContextHolder,
    private val identService: IdentService,
    private val gjenapneSoknadService: GjenapneSoknadService,
    private val sendSoknadService: SendSoknadService,
    private val hentSoknadService: HentSoknadService,
    private val metrikk: Metrikk,
    private val opprettSoknadService: OpprettSoknadService,
    private val korrigerSoknadService: KorrigerSoknadService,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val ettersendingSoknadService: EttersendingSoknadService,
    private val oppdaterSporsmalService: OppdaterSporsmalService,
    private val avbrytSoknadService: AvbrytSoknadService,
) {

    private val log = logger()

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseBody
    @GetMapping(value = ["/soknader"], produces = [APPLICATION_JSON_VALUE])
    fun hentSoknader(): List<RSSykepengesoknad> {
        val identer = hentIdenter()
        return hentSoknadService.hentSoknader(identer).map { it.tilRSSykepengesoknad() }
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/opprettSoknadUtland"], produces = [APPLICATION_JSON_VALUE])
    fun opprettSoknadUtland(): RSSykepengesoknad {
        return opprettSoknadService
            .opprettSoknadUtland(hentIdenter())
            .tilRSSykepengesoknad()
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/send"])
    fun sendSoknadRestful(@PathVariable("id") id: String) {
        val (soknadFraBase, identer) = hentOgSjekkTilgangTilSoknad(id)
        if (soknadFraBase.status == Soknadstatus.SENDT) {
            log.warn("Soknad ${soknadFraBase.id} er allerede sendt")
            return
        }
        soknadFraBase.validerSvarPaSoknad()

        try {
            sendSoknadService.sendSoknad(soknadFraBase, BRUKER, null, identer)
            log.info("Soknad sendt id: ${soknadFraBase.id}")
        } catch (e: Exception) {
            log.error("Innsending av søknad ${soknadFraBase.id} feilet")
            metrikk.tellInnsendingFeilet(soknadFraBase.soknadstype.name)
            throw e
        }
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/ettersendTilNav"])
    fun ettersendTilNav(@PathVariable("id") id: String) {
        val (soknadFraBase, _) = hentOgSjekkTilgangTilSoknad(id)

        log.info("Ettersender søknad: $id til NAV")
        ettersendingSoknadService.ettersendTilNav(soknadFraBase)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/ettersendTilArbeidsgiver"])
    fun ettersendTilArbeidsgiver(@PathVariable("id") id: String) {
        val (soknadFraBase, _) = hentOgSjekkTilgangTilSoknad(id)

        log.info("Ettersender søknad: $id til arbeidsgiver")
        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadFraBase)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/avbryt"])
    fun avbryt(@PathVariable("id") id: String) {
        val (soknadFraBase, _) = hentOgSjekkTilgangTilSoknad(id)

        log.info("Avbryter søknad: $id")
        if (Soknadstatus.AVBRUTT == soknadFraBase.status) {
            log.warn("Søknad med id $id er allerede avbrutt")
        } else {
            avbrytSoknadService.avbrytSoknad(soknadFraBase)
        }
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/gjenapne"])
    fun gjenapne(@PathVariable("id") id: String) {
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

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/korriger"])
    fun korriger(@PathVariable("id") id: String): RSSykepengesoknad {
        log.info("Ber om utkast til korrigering av: $id")
        val (soknad, identer) = hentOgSjekkTilgangTilSoknad(id)
        val utkast = korrigerSoknadService.finnEllerOpprettUtkast(soknad, identer)
        return utkast.tilRSSykepengesoknad()
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
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

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
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
        val (soknad, _) = hentOgSjekkTilgangTilSoknad(soknadId)

        val sporsmal = validerStatusOgHovedsporsmal(soknad = soknad, soknadId = soknadId, sporsmalId = sporsmalId)

        val oppdaterSporsmalResultat = oppdaterSporsmalService.lagreNyttSvar(soknad, sporsmalId, svar.mapSvar())
        return skapOppdaterSpmResponse(oppdaterSporsmalResultat, sporsmal.tag)
    }

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(
        value = ["/soknader/{soknadId}/sporsmal/{sporsmalId}/svar/{svarId}"]
    )
    fun slettSvar(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
        @PathVariable svarId: String,
    ) {
        val (soknad, _) = hentOgSjekkTilgangTilSoknad(soknadId)
        validerStatusOgHovedsporsmal(soknad = soknad, soknadId = soknadId, sporsmalId = sporsmalId)
        oppdaterSporsmalService.slettSvar(
            soknadFraBasenFørOppdatering = soknad,
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

    @ProtectedWithClaims(issuer = SELVBETJENING, claimMap = ["acr=Level4"])
    @PostMapping(value = ["/soknader/{id}/finnMottaker"], produces = [APPLICATION_JSON_VALUE])
    fun finnMottakerAvSoknad(@PathVariable("id") id: String): RSMottakerResponse {
        val (sykepengesoknad, identer) = hentOgSjekkTilgangTilSoknad(id)
        val mottaker = mottakerAvSoknadService.finnMottakerAvSoknad(sykepengesoknad, identer)
        return RSMottakerResponse(RSMottaker.valueOf(mottaker.name))
    }

    data class SoknadOgIdenter(val sykepengesoknad: Sykepengesoknad, val identer: FolkeregisterIdenter)

    private fun hentOgSjekkTilgangTilSoknad(soknadId: String): SoknadOgIdenter {
        val sykepengesoknad = hentSoknadService.finnSykepengesoknad(soknadId)
        val identer = hentIdenter()
        if (!identer.alle().contains(sykepengesoknad.fnr)) {
            throw IkkeTilgangException("Er ikke eier")
        }
        return SoknadOgIdenter(sykepengesoknad, identer)
    }

    private fun hentIdenter(): FolkeregisterIdenter {
        return Pair(identService, contextHolder).hentFolkeregisterIdenterMedHistorikk()
    }
}
