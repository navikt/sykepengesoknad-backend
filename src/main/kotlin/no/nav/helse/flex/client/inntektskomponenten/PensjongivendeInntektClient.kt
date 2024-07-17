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
    @Value("\${PENSJONSGIVENDE_INNTEKT_URL}")
    private val url: String,
) {
    val log = logger()

    fun hentPensjonsgivendeInntekter(fnr: String): List<HentPensjonsgivendeInntektResponse> {
        val uriBuilder =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/pensjonsgivendeinntektforfolketrygden")

        val headers = HttpHeaders()
        headers["Nav-Consumer-Id"] = "sykepengesoknad-backend"
        headers["Nav-Call-Id"] = UUID.randomUUID().toString()
        headers["rettighetspakke"] = "navSykepenger"
        headers["Nav-Personident"] = fnr
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)

        val treSisteAar = (LocalDate.now().year downTo LocalDate.now().minusYears(2).year).map { it.toString() }
        log.info("Tre siste år: ${treSisteAar.serialisertTilString()}")

        val pensjonsgivendeInntektSisteTreAar =
            treSisteAar.map { aar ->
                headers["inntektsaar"] = aar
                val result =
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
                    log.info("Kall mot Sigrun result body: ${it.serialisertTilString()}")
                    return@map it
                }

                val message = "Kall mot Sigrun returnerer ikke data"
                log.error(message)
                throw RuntimeException(message)
            }

        return pensjonsgivendeInntektSisteTreAar
    }
}
