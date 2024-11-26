package no.nav.helse.flex.client.sigrun

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.util.*

class PensjongivendeInntektClientException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

@Component
class PensjongivendeInntektClient(
    private val persongivendeInntektRestTemplate: RestTemplate,
    @Value("\${SIGRUN_URL}")
    private val url: String,
) {
    @Retryable(noRetryFor = [PensjongivendeInntektClientException::class], maxAttempts = 3)
    fun hentPensjonsgivendeInntekt(
        fnr: String,
        inntektsAar: Int,
    ): HentPensjonsgivendeInntektResponse {
        val uriBuilder = UriComponentsBuilder.fromHttpUrl("$url/api/v1/pensjonsgivendeinntektforfolketrygden")
        val headers =
            HttpHeaders().apply {
                this["Nav-Consumer-Id"] = "sykepengesoknad-backend"
                this["Nav-Call-Id"] = UUID.randomUUID().toString()
                this["rettighetspakke"] = "navSykepenger"
                this["Nav-Personident"] = fnr
                this["inntektsaar"] = inntektsAar.toString()
                this.contentType = MediaType.APPLICATION_JSON
                this.accept = listOf(MediaType.APPLICATION_JSON)
            }

        try {
            val response =
                persongivendeInntektRestTemplate.exchange(
                    uriBuilder.toUriString(),
                    HttpMethod.GET,
                    HttpEntity<Any>(headers),
                    HentPensjonsgivendeInntektResponse::class.java,
                )
            return response.body
                ?: throw RuntimeException("Responsen er null uten feilmelding fra server.")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND && e.responseBodyAsString.contains("PGIF-008")) {
                // 404 med tilhørende beskrivelse er ikke en feilsituasjon, men angir at det faktisk ikke finnes
                // pensjonsgivende inntekt for det forespurte året.
                return HentPensjonsgivendeInntektResponse(
                    norskPersonidentifikator = fnr,
                    inntektsaar = inntektsAar.toString(),
                    pensjonsgivendeInntekt = emptyList(),
                )
            }

            val feilmelding =
                when (e.statusCode) {
                    HttpStatus.NOT_FOUND ->
                        when {
                            e.responseBodyAsString.contains("PGIF-007") -> "Ikke treff på oppgitt personidentifikator."
                            e.responseBodyAsString.contains("PGIF-003") -> "Ukjent url benyttet."
                            else -> "Ressurs ikke funnet."
                        }

                    HttpStatus.UNAUTHORIZED ->
                        if (e.responseBodyAsString.contains("PGIF-004")) {
                            "Feil i forbindelse med autentisering."
                        } else {
                            "Uautorisert tilgang."
                        }

                    HttpStatus.FORBIDDEN ->
                        if (e.responseBodyAsString.contains("PGIF-005")) {
                            "Feil i forbindelse med autorisering."
                        } else {
                            "Forbudt tilgang."
                        }

                    HttpStatus.BAD_REQUEST ->
                        if (e.responseBodyAsString.contains("PGIF-006")) {
                            "Feil i forbindelse med validering av inputdata."
                        } else {
                            "Ugyldig forespørsel."
                        }

                    HttpStatus.NOT_ACCEPTABLE ->
                        if (e.responseBodyAsString.contains("PGIF-009")) {
                            "Feil tilknyttet dataformat. Kun JSON eller XML er støttet."
                        } else {
                            "Ugyldig dataformat."
                        }

                    HttpStatus.INTERNAL_SERVER_ERROR ->
                        when {
                            e.responseBodyAsString.contains("PGIF-001") -> "Uventet feil på tjenesten."
                            e.responseBodyAsString.contains("PGIF-002") -> "Uventet feil i et bakenforliggende system."
                            else -> "Serverfeil."
                        }

                    else -> "Klientfeil ved kall mot Sigrun: ${e.statusCode} - ${e.message}"
                }
            throw PensjongivendeInntektClientException(feilmelding, e)
        }
    }
}
