package no.nav.helse.flex.client.inntektskomponenten

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import java.util.*

class IngenPensjonsgivendeInntektFunnetException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

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

            return result.body
                ?: throw RuntimeException("Uventet feil: responsen er null selv om den ikke skulle være det.")
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == HttpStatus.NOT_FOUND && e.responseBodyAsString.contains("PGIF-008")) {
                throw IngenPensjonsgivendeInntektFunnetException(
                    "Ingen pensjonsgivende inntekt funnet på oppgitt personidentifikator og inntektsår.",
                    e,
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
            log.error(feilmelding, e)
            throw RuntimeException(feilmelding, e)
        } catch (e: Exception) {
            val message = "Feil ved kall mot Sigrun: ${e.message}"
            log.error(message, e)
            throw RuntimeException(message, e)
        }
    }

    fun hentPensjonsgivendeInntektForTreSisteArene(fnr: String): List<HentPensjonsgivendeInntektResponse>? {
        val treFerdigliknetAr = mutableListOf<HentPensjonsgivendeInntektResponse>()
        val naVarendeAr = LocalDate.now().year
        var arViHarSjekket = 0

        for (yearOffset in 0..4) {
            if (treFerdigliknetAr.size == 3) {
                break
            }

            val arViHenterFor = naVarendeAr - yearOffset
            val svar =
                try {
                    hentPensjonsgivendeInntekt(fnr, arViHenterFor)
                } catch (_: IngenPensjonsgivendeInntektFunnetException) {
                    HentPensjonsgivendeInntektResponse(
                        fnr,
                        arViHenterFor.toString(),
                        emptyList(),
                    )
                } catch (_: Exception) {
                    break
                }

            treFerdigliknetAr.add(svar)
            arViHarSjekket++
        }

        return if (treFerdigliknetAr.size == 3) treFerdigliknetAr else null
    }
}
