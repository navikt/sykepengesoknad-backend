package no.nav.helse.flex.client.aareg

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate

@Component
class AaregClient(
    @Value("\${AAREG_URL}")
    private val url: String,
    private val aaregRestTemplate: RestTemplate,
) {
    val log = logger()

    private val arbeidsforholdPath = "$url/api/v1/arbeidstaker/arbeidsforhold"
    private val navPersonident = "Nav-Personident"
    private val ansettelsesperiodeFomQueryParam = "ansettelsesperiodeFom"
    private val sporingsinformasjon = "sporingsinformasjon"

    fun hentArbeidsforhold(
        fnr: String,
        ansettelsesperiodeFom: LocalDate,
    ): List<Arbeidsforhold> {
        val uriBuilder =
            UriComponentsBuilder.fromHttpUrl(
                "$arbeidsforholdPath?" +
                    "$ansettelsesperiodeFomQueryParam=$ansettelsesperiodeFom&" +
                    "$sporingsinformasjon=false",
            )

        val headers = HttpHeaders()
        headers[navPersonident] = fnr

        val result =
            aaregRestTemplate
                .exchange(
                    uriBuilder.toUriString(),
                    HttpMethod.GET,
                    HttpEntity(
                        null,
                        headers,
                    ),
                    Array<Arbeidsforhold>::class.java,
                )

        if (result.statusCode != HttpStatus.OK) {
            val message = "Kall mot aareg feiler med HTTP-" + result.statusCode
            log.error(message)
            throw RuntimeException(message)
        }

        result.body?.let { return it.toList() }

        val message = "Kall mot aareg returnerer ikke data"
        log.error(message)
        throw RuntimeException(message)
    }
}
