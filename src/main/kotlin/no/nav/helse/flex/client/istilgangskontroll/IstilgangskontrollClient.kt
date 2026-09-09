package no.nav.helse.flex.client.istilgangskontroll

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpStatusCode
import org.springframework.http.MediaType
import org.springframework.resilience.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.exchange

@Component
class IstilgangskontrollClient(
    @param:Value("\${istilgangskontroll.url}") private val url: String,
    private val istilgangskontrollRestTemplate: RestTemplate,
) {
    companion object {
        const val ACCESS_TO_USER_WITH_AZURE_V2_PATH = "/api/tilgang/navident/person"
        const val NAV_PERSONIDENT_HEADER = "nav-personident"
    }

    val log = logger()

    // maxRetries teller forsøk etter det initielle kallet, så dette gir 3 kall totalt.
    @Retryable(maxRetries = 2)
    fun sjekkTilgangVeileder(fnr: String): Boolean {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_FORM_URLENCODED
        headers[NAV_PERSONIDENT_HEADER] = fnr

        return try {
            val response =
                istilgangskontrollRestTemplate.exchange<String>(
                    accessToUserV2Url(),
                    GET,
                    HttpEntity<Any>(headers),
                )
            response.statusCode.is2xxSuccessful
        } catch (e: HttpClientErrorException) {
            if (e.statusCode != HttpStatusCode.valueOf(403)) {
                log.error("Kall til istilgangskontroll feilet med HttpStatusCode ${e.statusCode.value()}", e)
            }
            false
        }
    }

    fun accessToUserV2Url(): String = "$url$ACCESS_TO_USER_WITH_AZURE_V2_PATH"
}
