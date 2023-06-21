package no.nav.helse.flex.client.inntektskomponenten

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.YearMonth
import java.util.*

@Component
class InntektskomponentenClient(
    private val inntektskomponentenRestTemplate: RestTemplate,
    @Value("\${INNTEKTSKOMPONENTEN_URL}")
    private val url: String
) {
    val log = logger()

    fun hentInntekter(fnr: String, fom: YearMonth, tom: YearMonth): HentInntekterResponse {
        val uriBuilder =
            UriComponentsBuilder.fromHttpUrl("$url/api/v1/hentinntektliste")

        val headers = HttpHeaders()
        headers["Nav-Consumer-Id"] = "sykepengesoknad-backend"
        headers["Nav-Call-Id"] = UUID.randomUUID().toString()
        headers.contentType = MediaType.APPLICATION_JSON
        headers.accept = listOf(MediaType.APPLICATION_JSON)

        val result = inntektskomponentenRestTemplate
            .exchange(
                uriBuilder.toUriString(),
                HttpMethod.POST,
                HttpEntity(
                    HentInntekterRequest(
                        maanedFom = fom,
                        maanedTom = tom,
                        formaal = "Sykepenger",
                        ainntektsfilter = "8-28",
                        ident = Aktoer(identifikator = fnr, aktoerType = "NATURLIG_IDENT")
                    ).serialisertTilString(),
                    headers
                ),
                HentInntekterResponse::class.java
            )

        if (result.statusCode != HttpStatus.OK) {
            val message = "Kall mot inntektskomponenten feiler med HTTP-" + result.statusCode
            log.error(message)
            throw RuntimeException(message)
        }

        result.body?.let { return it }

        val message = "Kall mot inntektskomponenten returnerer ikke data"
        log.error(message)
        throw RuntimeException(message)
    }
}
