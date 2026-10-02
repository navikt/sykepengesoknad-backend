package no.nav.helse.flex.client.flexsyketilfelle

import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.Sykepengesoknad
import no.nav.helse.flex.domain.mapper.SykepengesoknadTilSykepengesoknadDTOMapper
import no.nav.helse.flex.logger
import no.nav.helse.flex.service.FolkeregisterIdenter
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.util.objectMapper
import no.nav.syfo.sykmelding.kafka.model.SykmeldingKafkaMessageDTO
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod.POST
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

interface FlexSyketilfelleClient {
    fun hentSykeforloep(
        identer: FolkeregisterIdenter,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    ): List<Sykeforloep>

    fun hentVentetidForSykmelding(
        identer: FolkeregisterIdenter,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    ): VentetidForSykmeldingResponse

    fun beregnArbeidsgiverperiode(
        soknad: Sykepengesoknad,
        sykmelding: SykmeldingKafkaMessageDTO?,
        forelopig: Boolean,
        identer: FolkeregisterIdenter,
    ): Arbeidsgiverperiode?
}

@Component
class FlexSyketilfelleEksternClient(
    private val flexSyketilfelleRestTemplate: RestTemplate,
    private val sykepengesoknadTilSykepengesoknadDTOMapper: SykepengesoknadTilSykepengesoknadDTOMapper,
    @param:Value("\${flex.syketilfelle.url}")
    private val url: String,
) : FlexSyketilfelleClient {
    val log = logger()

    fun FolkeregisterIdenter.tilFnrHeader(): String = this.alle().joinToString(separator = ", ")

    @Retryable
    override fun hentSykeforloep(
        identer: FolkeregisterIdenter,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
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

        val response =
            flexSyketilfelleRestTemplate
                .exchange(
                    queryBuilder.toUriString(),
                    POST,
                    entity,
                    Array<Sykeforloep>::class.java,
                )

        if (!response.statusCode.is2xxSuccessful) {
            val message = "Kall til hent hentSykeforloep feilet med HTTP-${response.statusCode}"
            log.error(message)
            throw RuntimeException(message)
        }

        return response.body?.toList()
            ?: throw RuntimeException("Ingen data returnert fra flex-syketilfelle i hentSykeforloep")
    }

    @Retryable
    override fun hentVentetidForSykmelding(
        identer: FolkeregisterIdenter,
        sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
    ): VentetidForSykmeldingResponse {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.set("fnr", identer.tilFnrHeader())

        val queryBuilder =
            UriComponentsBuilder
                .fromUriString(url)
                .pathSegment("api", "v1", "ventetid", sykmeldingKafkaMessage.sykmelding.id, "ventetidForSykmelding")
                .queryParam("hentAndreIdenter", "false")

        val response =
            flexSyketilfelleRestTemplate
                .exchange(
                    queryBuilder.toUriString(),
                    POST,
                    HttpEntity(VentetidForSykmeldingRequest(sykmeldingKafkaMessage = sykmeldingKafkaMessage), headers),
                    VentetidForSykmeldingResponse::class.java,
                )

        if (!response.statusCode.is2xxSuccessful) {
            throw RuntimeException("Kall til ventetidForSykmelding feilet med HTTP-${response.statusCode}")
        }

        return response.body
            ?: throw RuntimeException("Ingen data returnert fra flex-syketilfelle ved kall til ventetidForSykmelding")
    }

    @Retryable
    override fun beregnArbeidsgiverperiode(
        soknad: Sykepengesoknad,
        sykmelding: SykmeldingKafkaMessageDTO?,
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

        val response =
            flexSyketilfelleRestTemplate
                .exchange(
                    queryBuilder.toUriString(),
                    POST,
                    HttpEntity(requestBody, headers),
                    Arbeidsgiverperiode::class.java,
                )

        if (!response.statusCode.is2xxSuccessful) {
            val message = "Kall til beregnArbeidsgiverperiode feilet med HTTP-${response.statusCode}"
            log.error(message)
            throw RuntimeException(message)
        }

        try {
            return response.body
        } catch (exception: Exception) {
            val message = "Feil ved beregning av arbeidsgiverperiode"
            log.error(message)
            throw RuntimeException(message, exception)
        }
    }

    private data class SoknadOgSykmelding(
        val soknad: SykepengesoknadDTO,
        val sykmelding: SykmeldingKafkaMessageDTO?,
    )
}

data class VentetidForSykmeldingRequest(
    val sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO? = null,
)

data class VentetidForSykmeldingResponse(
    val erUtenforVentetid: Boolean,
    val periodeMedSammeVentetid: List<SammeVentetidPeriode>,
)

data class SammeVentetidPeriode(
    val ressursId: String,
    val ventetid: Periode,
)

data class SykmeldingRequest(
    val sykmeldingKafkaMessage: SykmeldingKafkaMessageDTO,
)
