package no.nav.helse.flex.controller

import no.nav.helse.flex.config.OIDCIssuer.TOKENX
import no.nav.helse.flex.domain.Soknadstatus
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.parseEgenmeldingsdagerFraSykmelding
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.vedtaksperiodebehandling.VedtaksperiodeBehandlingRepository
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.security.token.support.core.context.TokenValidationContextHolder
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping(value = ["/api/v1/arbeidsgiver"])
class ArbeidsgiverInntektsmeldingController(
    private val contextHolder: TokenValidationContextHolder,
    private val identService: IdentService,
    private val hentSoknadService: HentSoknadService,
    private val vedtaksperiodeBehandlingRepository: VedtaksperiodeBehandlingRepository,
    @Value("\${SPINNTEKTSMELDING_FRONTEND_CLIENT_ID}")
    val spinntektsmeldingFrontendClientId: String,
) {
    @ProtectedWithClaims(issuer = TOKENX, combineWithOr = true, claimMap = ["acr=Level4", "acr=idporten-loa-high"])
    @PostMapping(
        value = ["/soknader"],
        produces = [APPLICATION_JSON_VALUE],
        consumes = [APPLICATION_JSON_VALUE],
    )
    fun hentSoknaderForInntektsmeldingFrontend(
        @RequestBody request: HentSoknaderRequest,
    ): List<HentSoknaderResponse> {
        contextHolder.validerTokenXClaims(spinntektsmeldingFrontendClientId)

        val identer = identService.hentFolkeregisterIdenterMedHistorikkForFnr(request.fnr)
        val soknader =
            hentSoknadService
                .hentSoknader(identer)
                .filter { it.fom != null }
                .filter { it.fom?.isAfter(request.eldsteFom.minusDays(1)) ?: false }
                .filter { it.arbeidsgiverOrgnummer == request.orgnummer }
                .filter { it.soknadstype == Soknadstype.ARBEIDSTAKERE }
                .filter { it.status == Soknadstatus.SENDT }
                .map { it.tilHentSoknaderResponse() }
                .sortedBy { it.fom }

        if (soknader.isEmpty()) {
            return emptyList()
        }
        val vedtaksperiodeMap = mutableMapOf<String, String>()
        vedtaksperiodeBehandlingRepository
            .finnVedtaksperiodeiderForSoknad(soknader.map { it.sykepengesoknadUuid })
            .forEach { vedtaksperiodeMap[it.sykepengesoknadUuid] = it.vedtaksperiodeId }

        return soknader.map {
            it.copy(
                vedtaksperiodeId = vedtaksperiodeMap[it.sykepengesoknadUuid],
            )
        }
    }
}

data class HentSoknaderRequest(
    val fnr: String,
    val eldsteFom: LocalDate,
    val orgnummer: String,
)

data class HentSoknaderResponse(
    val sykepengesoknadUuid: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val sykmeldingId: String,
    val status: Soknadstatus,
    val startSykeforlop: LocalDate,
    val egenmeldingsdagerFraSykmelding: List<LocalDate> = emptyList(),
    val vedtaksperiodeId: String?,
)

private fun Sykepengesoknad.tilHentSoknaderResponse(): HentSoknaderResponse =
    HentSoknaderResponse(
        status = this.status,
        sykepengesoknadUuid = this.id,
        fom = this.fom ?: throw RuntimeException("Fom kan ikke være null for arbeidstaker søknad"),
        tom = this.tom ?: throw RuntimeException("Tom kan ikke være null for arbeidstaker søknad"),
        startSykeforlop =
            this.startSykeforlop
                ?: throw RuntimeException("startSykeforlop kan ikke være null for arbeidstaker søknad"),
        sykmeldingId =
            this.sykmeldingId
                ?: throw RuntimeException("SykmeldingID kan ikke være null for arbeidstaker søknad"),
        egenmeldingsdagerFraSykmelding =
            this.egenmeldingsdagerFraSykmelding.parseEgenmeldingsdagerFraSykmelding()
                ?: emptyList(),
        vedtaksperiodeId = null,
    )
