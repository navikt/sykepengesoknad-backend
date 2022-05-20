package no.nav.helse.flex.client.flexsyketilfelle

import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.ErUtenforVentetidRequest
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.service.IdentService
import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpMethod.GET
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

@Component
class FlexSyketilfelleClient(
    private val flexSyketilfelleRestTemplate: RestTemplate,
    private val identService: IdentService,
    private val sykepengesoknadTilSykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper,
    @Value("\${flex.syketilfelle.url}")
    private val url: String
) {

    val log = logger()

    fun FolkeregisterIdenter.tilFnrHeader(): String = this.alle().joinToString(separator = ", ")

    @Retryable
    fun hentSykeforloep(identer: FolkeregisterIdenter): List<Sykeforloep> {

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("fnr", identer.tilFnrHeader())

        val queryBuilder = UriComponentsBuilder
            .fromHttpUrl(url)
            .pathSegment("api", "v1", "sykeforloep")
            .queryParam("hentAndreIdenter", "false")

        val result = flexSyketilfelleRestTemplate
            .exchange(
                queryBuilder.toUriString(),
                GET,
                HttpEntity(null, headers),
                Array<Sykeforloep>::class.java
            )

        if (!result.statusCode.is2xxSuccessful) {
            val message = "Kall mot flex-syketilfelle feiler med HTTP-${result.statusCode}"
            log.error(message)
            throw RuntimeException(message)
        }

        return result.body?.toList()
            ?: throw RuntimeException("Ingen data returnert fra flex-syketilfelle i hentSykeforloep")
    }

    @Retryable
    fun erUtenforVentetid(
        identer: FolkeregisterIdenter,
        sykmeldingId: String,
        erUtenforVentetidRequest: ErUtenforVentetidRequest
    ): Boolean {

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("fnr", identer.tilFnrHeader())

        val queryBuilder = UriComponentsBuilder
            .fromHttpUrl(url)
            .pathSegment("api", "v1", "ventetid", sykmeldingId, "erUtenforVentetid")
            .queryParam("hentAndreIdenter", "false")

        val result = flexSyketilfelleRestTemplate
            .exchange(
                queryBuilder.toUriString(),
                HttpMethod.POST,
                HttpEntity(erUtenforVentetidRequest, headers),
                Boolean::class.java
            )

        if (!result.statusCode.is2xxSuccessful) {
            val message = "Kall mot flex-syketilfelle feiler med HTTP-${result.statusCode}"
            log.error(message)
            throw RuntimeException(message)
        }

        return result.body
            ?: throw RuntimeException("Ingen data returnert fra flex-syketilfelle i erUtenforVentetid")
    }

    @Retryable
    fun beregnArbeidsgiverperiode(
        soknad: Sykepengesoknad,
        forelopig: Boolean,
        identer: FolkeregisterIdenter,
    ): Arbeidsgiverperiode? {
        val soknadDto = sykepengesoknadTilSykepengesoknadDTOMapper.mapTilSykepengesoknadDTO(soknad, null, false, false)

        val headers = HttpHeaders()
        headers.set("fnr", identer.tilFnrHeader())
        headers.set("forelopig", forelopig.toString()) // Hvis true så publiseres ikke juridisk vurdering

        val queryBuilder = UriComponentsBuilder
            .fromHttpUrl(url)
            .pathSegment("api", "v1", "arbeidsgiverperiode")
            .queryParam("hentAndreIdenter", "false")

        if (soknadDto.korrigerer != null) {
            queryBuilder.queryParam("andreKorrigerteRessurser", soknadDto.korrigerer)
        }

        val result = flexSyketilfelleRestTemplate
            .exchange(
                queryBuilder.toUriString(),
                HttpMethod.POST,
                HttpEntity(soknadDto, headers),
                Arbeidsgiverperiode::class.java
            )

        if (!result.statusCode.is2xxSuccessful) {
            val message = "Kall mot flex-syketilfelle feiler med HTTP-${result.statusCode}"
            log.error(message)
            throw RuntimeException(message)
        }

        try {
            return result.body
        } catch (exception: Exception) {
            val message = "Uventet feil ved beregning av arbeidsgiverperiode"
            log.error(message)
            throw RuntimeException(message, exception)
        }
    }
}
