package no.nav.helse.flex.controller

import io.swagger.v3.oas.annotations.Hidden
import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.*
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.domain.Soknadstype
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@Hidden
class SoknadKafkaFormatController(
    private val clientIdValidation: ClientIdValidation,
    private val hentSoknadService: HentSoknadService,
    private val sykepengesoknadTilSykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper,
    private val identService: IdentService,
) {
    @ProtectedWithClaims(issuer = AZUREATOR)
    @ResponseBody
    @GetMapping(
        value = ["/api/v3/soknader/{id}/kafkaformat", "/api/v3/soknader/{id}"],
        produces = [APPLICATION_JSON_VALUE],
    )
    fun hentSoknad(
        @PathVariable id: String,
    ): SykepengesoknadDTO {
        clientIdValidation.validateClientId(
            listOf(
                NamespaceAndApp(namespace = "flex", app = "sykepengesoknad-arkivering-oppgave"),
                NamespaceAndApp(namespace = "tbd", app = "sparkel-dokumenter"),
                NamespaceAndApp(namespace = "speilvendt", app = "bakrommet"),
            ),
        )
        return hentSoknadService.finnSykepengesoknad(id).tilSykepengesoknadDto()
    }

    data class HentSoknaderRequest(
        val fnr: String,
        val medSporsmal: Boolean = false,
        val fom: LocalDate,
        val tom: LocalDate,
    )

    @ProtectedWithClaims(issuer = AZUREATOR)
    @ResponseBody
    @PostMapping(
        value = ["/api/v3/soknader"],
        produces = [APPLICATION_JSON_VALUE],
        consumes = [APPLICATION_JSON_VALUE],
    )
    fun hentSoknader(
        @RequestBody req: HentSoknaderRequest,
    ): List<SykepengesoknadDTO> {
        clientIdValidation.validateClientId(
            listOf(
                NamespaceAndApp(namespace = "speilvendt", app = "bakrommet"),
            ),
        )

        fun SykepengesoknadDTO.mapBortSporsmal(): SykepengesoknadDTO {
            if (req.medSporsmal) {
                return this
            }
            return this.copy(
                sporsmal = emptyList(),
            )
        }

        fun Sykepengesoknad.filterMedDato(): Boolean {
            if (this.fom?.isBefore(req.fom) == true) {
                return false
            }
            if (this.tom?.isAfter(req.tom) == true) {
                return false
            }
            return true
        }

        return hentSoknadService
            .hentSoknader(identService.hentFolkeregisterIdenterMedHistorikkForFnr(req.fnr))
            .filter { it.soknadstype != Soknadstype.OPPHOLD_UTLAND }
            .filter { it.filterMedDato() }
            .map { it.tilSykepengesoknadDto() }
            .map { it.mapBortSporsmal() }
    }

    private fun Sykepengesoknad.tilSykepengesoknadDto(): SykepengesoknadDTO =
        sykepengesoknadTilSykepengesoknadDTOMapper.mapTilSykepengesoknadDTO(
            sykepengesoknad = this,
            mottaker = null,
            erEttersending = false,
            endeligVurdering = false,
        )
}

class IngenTilgangException(
    s: String,
) : RuntimeException(s)
