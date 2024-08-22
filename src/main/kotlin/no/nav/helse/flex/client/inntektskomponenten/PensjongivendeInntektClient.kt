package no.nav.helse.flex.client.inntektskomponenten

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

@Component
class PensjongivendeInntektClient(
    private val persongivendeInntektRestTemplate: RestTemplate,
    @Value("\${SIGRUN_URL}")
    private val url: String,
) {
    val log = logger()

    @Retryable
    fun hentPensjonsgivendeInntekt(
        fnr: String,
        arViHenterFor: Int,
    ): HentPensjonsgivendeInntektResponse {
        val uriBuilder =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/pensjonsgivendeinntektforfolketrygden")

        val headers =
            HttpHeaders().apply {
                this["Nav-Consumer-Id"] = "sykepengesoknad-backend"
                this["Nav-Call-Id"] = UUID.randomUUID().toString()
                this["rettighetspakke"] = "navSykepenger"
                this["Nav-Personident"] = fnr
                this["inntektsaar"] = arViHenterFor.toString()
                this.contentType = MediaType.APPLICATION_JSON
                this.accept = listOf(MediaType.APPLICATION_JSON)
            }

        try {
            val result =
                persongivendeInntektRestTemplate.exchange(
                    uriBuilder.toUriString(),
                    HttpMethod.GET,
                    HttpEntity<Any>(headers),
                    HentPensjonsgivendeInntektResponse::class.java,
                )

            if (result.statusCode == HttpStatus.NOT_FOUND) {
                val message = "Ingen pensjonsgivende inntekt funnet for år $arViHenterFor på personidentifikator $fnr"
                log.warn(message)
                throw IngenPensjonsgivendeInntektFunnetException(message)
            }

            if (result.statusCode != HttpStatus.OK) {
                val message = "Kall mot Sigrun feiler med HTTP-${result.statusCode}"
                log.error(message)
                throw RuntimeException(message)
            }

            return result.body ?: throw RuntimeException("Uventet feil: responsen er null selv om den ikke skulle være det.")
        } catch (e: HttpClientErrorException) {
            val message =
                if (e.statusCode == HttpStatus.NOT_FOUND && e.responseBodyAsString.contains("PGIF-008")) {
                    "Ingen pensjonsgivende inntekt funnet på oppgitt personidentifikator og inntektsår."
                } else {
                    "Klientfeil ved kall mot Sigrun: ${e.statusCode} - ${e.message}"
                }
            log.error(message, e)
            throw RuntimeException(message, e)
        } catch (e: Exception) {
            val message = "Feil ved kall mot Sigrun: ${e.message}"
            log.error(message, e)
            throw RuntimeException(message, e)
        }
    }
}

class IngenPensjonsgivendeInntektFunnetException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
