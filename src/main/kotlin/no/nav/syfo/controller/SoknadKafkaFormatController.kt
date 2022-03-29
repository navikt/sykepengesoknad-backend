package no.nav.syfo.controller

import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.security.token.support.core.api.ProtectedWithClaims
import no.nav.syfo.clientidvalidation.ClientIdValidation
import no.nav.syfo.clientidvalidation.ClientIdValidation.*
import no.nav.syfo.config.OIDCIssuer.AZUREATOR
import no.nav.syfo.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.syfo.service.HentSoknadService
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RestController

@RestController
class SoknadKafkaFormatController(
    private val clientIdValidation: ClientIdValidation,
    private val hentSoknadService: HentSoknadService,
    private val sykepengesoknadTilSykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper,
) {

    @ProtectedWithClaims(issuer = AZUREATOR)
    @ResponseBody
    @GetMapping(value = ["/api/v3/soknader/{id}/kafkaformat"], produces = [APPLICATION_JSON_VALUE])
    fun soknadMedKafkaFormatv3(@PathVariable("id") id: String): SykepengesoknadDTO {
        clientIdValidation.validateClientId(
            listOf(
                NamespaceAndApp(namespace = "flex", app = "flex-fss-proxy"),
            )
        )
        val sykepengesoknad = hentSoknadService.finnSykepengesoknad(id)
        val dto = sykepengesoknadTilSykepengesoknadDTOMapper.mapTilSykepengesoknadDTO(sykepengesoknad, null, false, false)
        return dto
    }
}

class IngenTilgangException(s: String) : RuntimeException(s)
