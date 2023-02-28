package no.nav.helse.flex.client.inntektskomponenten

import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.YearMonth

@Component
class InntektskomponentenClient(
    private val flexFssProxyRestTemplate: RestTemplate,
    @Value("\${FLEX_FSS_PROXY_URL}") private val flexFssProxyUrl: String

) {

    val log = logger()

    fun hentInntekter(fnr: String, fom: YearMonth, tom: YearMonth): HentInntekterResponse {
        val uriBuilder =
            UriComponentsBuilder.fromHttpUrl("$flexFssProxyUrl/api/inntektskomponenten/api/v1/hentinntektliste")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val result = flexFssProxyRestTemplate
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

        if (result.statusCode != OK) {
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
