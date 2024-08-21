package no.nav.helse.flex.client.inntektskomponenten

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import java.util.*

@Component
class PensjongivendeInntektClient(
    private val persongivendeInntektRestTemplate: RestTemplate,
    @Value("\${SIGRUN_URL}")
    private val url: String,
) {
    val log = logger()

    fun hentPensjonsgivendeInntekt(
        fnr: String,
        arViHenterFor: Int,
    ): HentPensjonsgivendeInntektResponse? {
        // TODO: Fjern dette før prodsetting!!
        log.info("fnr som vi sender med kallet er: $fnr")
        val uriBuilder =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/pensjonsgivendeinntektforfolketrygden")

        val headers = HttpHeaders()
        headers["Nav-Consumer-Id"] = "sykepengesoknad-backend"
        headers["Nav-Call-Id"] = UUID.randomUUID().toString()
        headers["rettighetspakke"] = "navSykepenger"
        headers["Nav-Personident"] = fnr
        headers["inntektsaar"] = arViHenterFor.toString()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)

        val result: ResponseEntity<HentPensjonsgivendeInntektResponse>
        try {
            result =
                persongivendeInntektRestTemplate
                    .exchange(
                        uriBuilder.toUriString(),
                        HttpMethod.GET,
                        HttpEntity<Any>(headers),
                        HentPensjonsgivendeInntektResponse::class.java,
                    )
            if (result.statusCode != HttpStatus.OK) {
                val message = "Kall mot Sigrun feiler med HTTP-" + result.statusCode
                log.error(message)
                throw RuntimeException(message)
            }

            log.info("Kall mot Sigrun result: ${result.serialisertTilString()}")
            result.body?.let {
                return it
            }
        } catch (e: Exception) {
            val message = "Kall mot Sigrun returnerer ikke data"
            log.error(message, e)
            throw RuntimeException(message)
        }
        return result.body!!
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
            val svar = hentPensjonsgivendeInntekt(fnr, arViHenterFor)

            if (svar != null) {
                treFerdigliknetAr.add(svar)
            }
            arViHarSjekket++
        }

        return if (treFerdigliknetAr.size == 3) treFerdigliknetAr else null
    }
}
