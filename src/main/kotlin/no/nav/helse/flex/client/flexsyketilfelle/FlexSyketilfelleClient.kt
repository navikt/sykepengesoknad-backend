package no.nav.helse.flex.client.flexsyketilfelle

import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.VentetidRequest
import no.nav.helse.flex.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.helse.flex.domain.sykmelding.SykmeldingKafkaMessage
import no.nav.helse.flex.domain.sykmelding.SykmeldingRequest
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.objectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod.POST
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class FlexSyketilfelleClient(
    private val flexSyketilfelleRestTemplate: RestTemplate,
    private val sykepengesoknadTilSykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper,
    @param:Value("\${flex.syketilfelle.url}")
    private val url: String,
) {
    val log = logger()

    fun FolkeregisterIdenter.tilFnrHeader(): String = this.alle().joinToString(separator = ", ")

    @Retryable
    fun hentSykeforloep(
        identer: FolkeregisterIdenter,
        sykmeldingKafkaMessage: SykmeldingKafkaMessage,
    ): List<Sykeforloep> {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("fnr", identer.tilFnrHeader())

        val queryBuilder =
            UriComponentsBuilder
                .fromUriString(url)
                .pathSegment("api", "v1", "sykeforloep")
                .queryParam("hentAndreIdenter", "false")

        val sykmeldingRequest = SykmeldingRequest(sykmeldingKafkaMessage)
        val entity = HttpEntity(objectMapper.writeValueAsString(sykmeldingRequest), headers)

        val result =
            flexSyketilfelleRestTemplate
                .exchange(
                    queryBuilder.toUriString(),
                    POST,
                    entity,
                    Array<Sykeforloep>::class.java,
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
        ventetidRequest: VentetidRequest,
    ): Boolean {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("fnr", identer.tilFnrHeader())

        val queryBuilder =
            UriComponentsBuilder
                .fromUriString(url)
                .pathSegment("api", "v1", "ventetid", sykmeldingId, "erUtenforVentetid")
                .queryParam("hentAndreIdenter", "false")

        val result =
            flexSyketilfelleRestTemplate
                .exchange(
                    queryBuilder.toUriString(),
                    POST,
                    HttpEntity(ventetidRequest, headers),
                    Boolean::class.java,
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
        sykmelding: SykmeldingKafkaMessage?,
        forelopig: Boolean,
        identer: FolkeregisterIdenter,
    ): Arbeidsgiverperiode? {
        val soknadDto =
            sykepengesoknadTilSykepengesoknadDTOMapper.mapTilSykepengesoknadDTO(
                sykepengesoknad = soknad,
                mottaker = null,
                erEttersending = false,
                endeligVurdering = false,
            )
        val requestBody = SoknadOgSykmelding(soknadDto, sykmelding)

        val headers = HttpHeaders()
        headers.set("fnr", identer.tilFnrHeader())
        headers.set("forelopig", forelopig.toString()) // Hvis true så publiseres ikke juridisk vurdering

        val queryBuilder =
            UriComponentsBuilder
                .fromUriString(url)
                .pathSegment("api", "v2", "arbeidsgiverperiode")
                .queryParam("hentAndreIdenter", "false")

        if (soknadDto.korrigerer != null) {
            queryBuilder.queryParam("andreKorrigerteRessurser", soknadDto.korrigerer)
        }

        val result =
            flexSyketilfelleRestTemplate
                .exchange(
                    queryBuilder.toUriString(),
                    POST,
                    HttpEntity(requestBody, headers),
                    Arbeidsgiverperiode::class.java,
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

    private data class SoknadOgSykmelding(
        val soknad: SykepengesoknadDTO,
        val sykmelding: SykmeldingKafkaMessage?,
    )
}
