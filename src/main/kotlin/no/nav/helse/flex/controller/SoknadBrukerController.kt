package no.nav.helse.flex.controller

import io.swagger.v3.oas.annotations.tags.Tag
import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.config.OIDCIssuer.TOKENX
import no.nav.helse.flex.controller.domain.RSMottakerResponse
import no.nav.helse.flex.controller.domain.RSOppdaterSporsmalResponse
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSMottaker
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknadMetadata
import no.nav.helse.flex.controller.mapper.mapSporsmal
import no.nav.helse.flex.controller.mapper.mapSporsmalTilRs
import no.nav.helse.flex.controller.mapper.mapSvar
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknad
import no.nav.helse.flex.controller.mapper.tilRSSykepengesoknadMetadata
import no.nav.helse.flex.domain.Avsendertype.BRUKER
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Sporsmal
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.exception.FeilStatusForOppdaterSporsmalException
import no.nav.helse.flex.exception.ForsokPaSendingAvNyereSoknadException
import no.nav.helse.flex.exception.IkkeTilgangException
import no.nav.helse.flex.exception.ReadOnlyException
import no.nav.helse.flex.exception.SporsmalFinnesIkkeISoknadException
import no.nav.helse.flex.frisktilarbeid.FjernFremtidigeFtaSoknaderService
import no.nav.helse.flex.inntektsopplysninger.InntektsopplysningForNaringsdrivende
import no.nav.helse.flex.logger
import no.nav.helse.flex.oppdatersporsmal.soknad.OppdaterSporsmalService
import no.nav.helse.flex.sending.SoknadSender
import no.nav.helse.flex.service.AvbrytSoknadService
import no.nav.helse.flex.service.EttersendingSoknadService
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.GjenapneSoknadService
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.service.KorrigerSoknadService
import no.nav.helse.flex.service.MottakerAvSoknadService
import no.nav.helse.flex.service.OppholdUtenforEOSService
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_EOS
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.OpprettSoknadService
import no.nav.helse.flex.svarvalidering.validerSvarPaSoknad
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import no.nav.security.token.support.core.jwt.JwtTokenClaims
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping(value = ["/api/v2"])
@Tag(name = "sykepengesøknader", description = "Operasjoner for å hente og manipulere sykepengesøknader")
class SoknadBrukerController(
    private val contextHolder: TokenValidationContextHolder,
    private val identService: IdentService,
    private val gjenapneSoknadService: GjenapneSoknadService,
    private val soknadSender: SoknadSender,
    private val hentSoknadService: HentSoknadService,
    private val opprettSoknadService: OpprettSoknadService,
    private val korrigerSoknadService: KorrigerSoknadService,
    private val mottakerAvSoknadService: MottakerAvSoknadService,
    private val ettersendingSoknadService: EttersendingSoknadService,
    private val oppdaterSporsmalService: OppdaterSporsmalService,
    private val avbrytSoknadService: AvbrytSoknadService,
    private val environmentToggles: EnvironmentToggles,
    private val inntektsopplysningForNaringsdrivende: InntektsopplysningForNaringsdrivende,
    private val oppholdUtenforEOSService: OppholdUtenforEOSService,
    private val fjernFremtidigeFtaSoknaderService: FjernFremtidigeFtaSoknaderService,
    @param:Value("\${DITT_SYKEFRAVAER_FRONTEND_CLIENT_ID}")
    val dittSykefravaerFrontendClientId: String,
    @param:Value("\${SYKEPENGESOKNAD_FRONTEND_CLIENT_ID}")
    val sykepengesoknadFrontendClientId: String,
) {
    private val log = logger()

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @ResponseBody
    @GetMapping(value = ["/soknader/metadata"], produces = [APPLICATION_JSON_VALUE])
    fun hentSoknaderMetadata(): List<RSSykepengesoknadMetadata> {
        val identer =
            contextHolder.validerTokenXClaims(dittSykefravaerFrontendClientId, sykepengesoknadFrontendClientId).hentIdenter()
        return hentSoknadService.hentSoknaderUtenSporsmal(identer).map { it.tilRSSykepengesoknadMetadata() }
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @ResponseBody
    @GetMapping(value = ["/soknad/{id}"], produces = [APPLICATION_JSON_VALUE])
    fun hentSoknad(
        @PathVariable id: String,
    ): RSSykepengesoknad {
        val (soknad, identer) = hentOgSjekkTilgangTilSoknad(id)

        return soknad.utvidSoknadMedKorrigeringsfristUtlopt(identer).tilRSSykepengesoknad()
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @PostMapping(value = ["/opprettSoknadUtland"], produces = [APPLICATION_JSON_VALUE])
    fun opprettSoknadUtland(): RSSykepengesoknad {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val identer = contextHolder.validerTokenXClaims(sykepengesoknadFrontendClientId).hentIdenter()
        return opprettSoknadService
            .opprettSoknadUtland(identer)
            .tilRSSykepengesoknad()
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @PostMapping(value = ["/soknader/{id}/send"])
    fun sendSoknadRestful(
        @PathVariable id: String,
    ) {
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
                "Forsøk på sending av en nyere søknad med id: ${soknadFraBase.id}.",
            )
        }

        val sendtSoknad =
            try {
                soknadSender.sendSoknad(soknadFraBase, BRUKER, null, identer).also {
                    log.info("Soknad sendt id: ${soknadFraBase.id}")
                }
            } catch (e: Exception) {
                log.error("Innsending av søknad ${soknadFraBase.id} feilet")
                throw e
            }
        oppholdUtenforEOSService.skalOppretteSoknadForOppholdUtenforEOS(sendtSoknad, identer)
        fjernFremtidigeFtaSoknaderService.fjernFremtidigeFriskmeldtSoknaderHvisFerdig(sendtSoknad)

        try {
            inntektsopplysningForNaringsdrivende.lagreOpplysningerOmDokumentasjonAvInntektsopplysninger(sendtSoknad)
        } catch (e: Exception) {
            log.error("Henting og lagring av inntektsopplysninger for feilet for søknad ${sendtSoknad.id}.")
        }
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @PostMapping(value = ["/soknader/{id}/ettersendTilArbeidsgiver"])
    fun ettersendTilArbeidsgiver(
        @PathVariable id: String,
    ) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknadFraBase, _) = hentOgSjekkTilgangTilSoknad(id)

        log.info("Ettersender søknad: $id til arbeidsgiver")
        ettersendingSoknadService.ettersendTilArbeidsgiver(soknadFraBase)
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @PostMapping(value = ["/soknader/{id}/avbryt"])
    fun avbryt(
        @PathVariable id: String,
    ) {
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

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @PostMapping(value = ["/soknader/{id}/gjenapne"])
    fun gjenapne(
        @PathVariable id: String,
    ) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknadFraBase, _) = hentOgSjekkTilgangTilSoknad(id)

        if (soknadFraBase.status !== Soknadstatus.AVBRUTT) {
            log.info("Kan ikke gjenåpne søknad som ikke er avbrutt: ${soknadFraBase.id}")
            throw IllegalArgumentException("Kan ikke gjenåpne søknad som ikke er avbrutt")
        }
        log.info("Gjenåpner søknad: ${soknadFraBase.id}")
        gjenapneSoknadService.gjenapneSoknad(soknadFraBase)
        return
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @PostMapping(value = ["/soknader/{id}/korriger"], produces = [APPLICATION_JSON_VALUE])
    fun korriger(
        @PathVariable id: String,
    ): RSSykepengesoknad {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknad, identer) = hentOgSjekkTilgangTilSoknad(id)
        log.info("Ber om utkast til korrigering av: ${soknad.id}")
        val utkast = korrigerSoknadService.finnEllerOpprettUtkast(soknad, identer)
        return utkast.tilRSSykepengesoknad()
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @PutMapping(
        value = ["/soknader/{soknadId}/sporsmal/{sporsmalId}"],
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun oppdaterSporsmal(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
        @RequestBody rsSporsmal: RSSporsmal,
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

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(
        value = ["/soknader/{soknadId}/sporsmal/{sporsmalId}/svar"],
        consumes = [APPLICATION_JSON_VALUE],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun lagreNyttSvar(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
        @RequestBody svar: RSSvar,
    ): RSOppdaterSporsmalResponse {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknad, _) = hentOgSjekkTilgangTilSoknad(soknadId)

        val sporsmal = validerStatusOgHovedsporsmal(soknad = soknad, soknadId = soknadId, sporsmalId = sporsmalId)

        val oppdaterSporsmalResultat = oppdaterSporsmalService.lagreNyttSvar(soknad, sporsmalId, svar.mapSvar())
        return skapOppdaterSpmResponse(oppdaterSporsmalResultat, sporsmal.tag)
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(value = ["/soknader/{soknadId}/sporsmal/{sporsmalId}/svar/{svarId}"])
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
            svarId = svarId,
        )
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @GetMapping(value = ["/soknader/{id}/mottaker"], produces = [APPLICATION_JSON_VALUE])
    fun hentMottakerAvSoknad(
        @PathVariable id: String,
    ): RSMottakerResponse {
        val (sykepengesoknad, identer) = hentOgSjekkTilgangTilSoknad(id)
        val mottaker = mottakerAvSoknadService.finnMottakerAvSoknad(sykepengesoknad, identer)
        return RSMottakerResponse(RSMottaker.valueOf(mottaker.name))
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping(value = ["/soknader/{soknadId}/sporsmal/{sporsmalId}/undersporsmal"])
    fun leggTilUndersporsmal(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
    ) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknad, _) = hentOgSjekkTilgangTilSoknad(soknadId)
        val sporsmal = validerStatusOgHovedsporsmal(soknad = soknad, soknadId = soknadId, sporsmalId = sporsmalId)
        // Det er bare spørsmål om medlemskap i folketrygden som trenger grupperte underspørsmål.
        if (listOf(
                MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
            ).contains(sporsmal.tag)
        ) {
            oppdaterSporsmalService.leggTilNyttUndersporsmal(soknad.id, sporsmal.tag)
        } else {
            throw IllegalArgumentException("Kan ikke legge til underspørsmål på spørsmål med tag ${sporsmal.tag}.")
        }
    }

    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @DeleteMapping(value = ["/soknader/{soknadId}/sporsmal/{sporsmalId}/undersporsmal/{undersporsmalId}"])
    fun slettUndersporsmal(
        @PathVariable soknadId: String,
        @PathVariable sporsmalId: String,
        @PathVariable undersporsmalId: String,
    ) {
        if (environmentToggles.isReadOnly()) {
            throw ReadOnlyException()
        }
        val (soknad, _) = hentOgSjekkTilgangTilSoknad(soknadId)
        val sporsmal = validerStatusOgHovedsporsmal(soknad, soknadId, sporsmalId)
        // Det er bare spørsmål om medlemskap i folketrygden som trenger grupperte underspørsmål.
        if (listOf(
                MEDLEMSKAP_UTFORT_ARBEID_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_NORGE,
                MEDLEMSKAP_OPPHOLD_UTENFOR_EOS,
            ).contains(sporsmal.tag)
        ) {
            oppdaterSporsmalService.slettUndersporsmal(soknad, sporsmal, undersporsmalId)
        } else {
            throw IllegalArgumentException("Kan ikke slette underspørsmål på spørsmål med tag ${sporsmal.tag}.")
        }
    }

    data class SoknadOgIdenter(
        val sykepengesoknad: Sykepengesoknad,
        val identer: FolkeregisterIdenter,
    )

    private fun hentOgSjekkTilgangTilSoknad(soknadId: String): SoknadOgIdenter {
        val identer = contextHolder.validerTokenXClaims(sykepengesoknadFrontendClientId).hentIdenter()
        val sykepengesoknad = hentSoknadService.finnSykepengesoknad(soknadId)
        if (!identer.alle().contains(sykepengesoknad.fnr)) {
            throw IkkeTilgangException("Er ikke eier")
        }
        return SoknadOgIdenter(sykepengesoknad, identer)
    }

    private fun validerStatusOgHovedsporsmal(
        soknad: Sykepengesoknad,
        soknadId: String,
        sporsmalId: String,
    ): Sporsmal {
        if (!listOf(Soknadstatus.NY, Soknadstatus.UTKAST_TIL_KORRIGERING).contains(soknad.status)) {
            val spm = soknad.sporsmal.find { it.id == sporsmalId }
            throw FeilStatusForOppdaterSporsmalException(
                "Søknad $soknadId har status ${soknad.status}. Da kan man ikke besvare spørsmål ${spm?.tag}",
            )
        }

        if (!soknad.alleSporsmalOgUndersporsmal().mapNotNull { it.id }.contains(sporsmalId)) {
            throw SporsmalFinnesIkkeISoknadException("$sporsmalId finnes ikke i søknad $soknadId.")
        }

        return soknad.sporsmal.find { it.id == sporsmalId }
            ?: throw IllegalArgumentException("$sporsmalId er ikke et hovedspørsmål i søknad $soknadId.")
    }

    private fun Sykepengesoknad.utvidSoknadMedKorrigeringsfristUtlopt(identer: FolkeregisterIdenter): Sykepengesoknad =
        korrigerSoknadService.utvidSoknadMedKorrigeringsfristUtlopt(this, identer)

    private fun JwtTokenClaims.hentIdenter(): FolkeregisterIdenter =
        identService.hentFolkeregisterIdenterMedHistorikkForFnr(this.getStringClaim("pid"))

    private fun skapOppdaterSpmResponse(
        oppdaterSporsmalResultat: OppdaterSporsmalService.OppdaterSporsmalResultat,
        sporsmalTag: String,
    ): RSOppdaterSporsmalResponse {
        val sporsmalSomBleOppdatert = oppdaterSporsmalResultat.oppdatertSoknad.sporsmal.find { it.tag == sporsmalTag }!!

        if (oppdaterSporsmalResultat.mutert) {
            return RSOppdaterSporsmalResponse(
                mutertSoknad = oppdaterSporsmalResultat.oppdatertSoknad.tilRSSykepengesoknad(),
                oppdatertSporsmal = sporsmalSomBleOppdatert.mapSporsmalTilRs(),
            )
        }
        return RSOppdaterSporsmalResponse(
            mutertSoknad = null,
            oppdatertSporsmal = sporsmalSomBleOppdatert.mapSporsmalTilRs(),
        )
    }
}
