package no.nav.helse.flex.client.innsendingapi

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class InnsendingApiClient(
    @Value("\${INNSENDING_API_URL}")
    private val url: String,
    private val innsendingApiRestTemplate: RestTemplate,
) {
    val log = logger()

    fun opprettEttersending(request: EttersendingRequest): EttersendingResponse {
        val uriBuilder =
            UriComponentsBuilder.fromHttpUrl("$url/ekstern/v1/ettersending")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val result =
            innsendingApiRestTemplate
                .exchange(
                    uriBuilder.toUriString(),
                    HttpMethod.POST,
                    HttpEntity(
                        request.serialisertTilString(),
                        headers,
                    ),
                    EttersendingResponse::class.java,
                )

        if (!result.statusCode.is2xxSuccessful) {
            val message = "Kall mot innsending-api feiler med HTTP-" + result.statusCode
            log.error(message)
            throw RuntimeException(message)
        }

        result.body?.let { return it }

        val message = "Kall mot innsending-api returnerer ikke data"
        log.error(message)
        throw RuntimeException(message)
    }

    fun slett(innsendingsId: String) {
        val uriBuilder =
            UriComponentsBuilder.fromHttpUrl("$url/ekstern/v1/ettersending/$innsendingsId")

        try {
            innsendingApiRestTemplate
                .delete(
                    uriBuilder.toUriString(),
                )
        } catch (e: Exception) {
            log.error("Feil ved sletting av innsending", e)
            throw e
        }
    }
}

data class EttersendingRequest(
    val skjemanr: String,
    val sprak: String,
    val tema: String,
    val vedleggsListe: List<Vedlegg>,
    val brukernotifikasjonstype: String?,
    val koblesTilEksisterendeSoknad: Boolean,
)

data class Vedlegg(
    val vedleggsnr: String,
    val tittel: String,
)

data class EttersendingResponse(
    val innsendingsId: String,
)
