package no.nav.helse.flex.controller

import no.nav.helse.flex.clientidvalidation.ClientIdValidation
import no.nav.helse.flex.clientidvalidation.ClientIdValidation.*
import no.nav.helse.flex.config.OIDCIssuer.AZUREATOR
import no.nav.helse.flex.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.helse.flex.service.HentSoknadService
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.security.token.support.core.api.ProtectedWithClaims
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SoknadKafkaFormatController(
    private val clientIdValidation: ClientIdValidation,
    private val hentSoknadService: HentSoknadService,
    private val sykepengesoknadTilSykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper
) {

    @ProtectedWithClaims(issuer = AZUREATOR)
    @ResponseBody
    @GetMapping(value = ["/api/v3/soknader/{id}/kafkaformat"], produces = [APPLICATION_JSON_VALUE])
    fun soknadMedKafkaFormatv3(@PathVariable("id") id: String): SykepengesoknadDTO {
        clientIdValidation.validateClientId(
            listOf(
                NamespaceAndApp(namespace = "flex", app = "sykepengesoknad-korrigering-metrikk"),
                NamespaceAndApp(namespace = "flex", app = "sykepengesoknad-arkivering-oppgave"),
                NamespaceAndApp(namespace = "tbd", app = "sparkel-dokumenter")
            )
        )
        val sykepengesoknad = hentSoknadService.finnSykepengesoknad(id)
        val dto = sykepengesoknadTilSykepengesoknadDTOMapper.mapTilSykepengesoknadDTO(
            sykepengesoknad = sykepengesoknad,
            mottaker = null,
            erEttersending = false,
            endeligVurdering = false
        )
        return dto
    }
}

class IngenTilgangException(s: String) : RuntimeException(s)
