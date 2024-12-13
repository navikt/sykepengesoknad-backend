package no.nav.helse.flex.client.ereg

import no.nav.helse.flex.config.EnvironmentToggles
import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class EregClient(
    private val plainRestTemplate: RestTemplate,
    private val environmentToggles: EnvironmentToggles,
    @Value("\${EREG_URL}") private val eregUrl: String,
) {
    val log = logger()

    fun hentBedrift(virksomhetsnummer: String): Nokkelinfo {
        try {
            val uriBuilder =
                UriComponentsBuilder.fromUriString("$eregUrl/v2/organisasjon/$virksomhetsnummer/noekkelinfo")

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val result =
                plainRestTemplate
                    .exchange(
                        uriBuilder.toUriString(),
                        HttpMethod.GET,
                        HttpEntity<Any>(headers),
                        Nokkelinfo::class.java,
                    )

            if (result.statusCode != OK) {
                val message = "Kall mot syfosoknad feiler med HTTP-" + result.statusCode
                log.error(message)
                throw RuntimeException(message)
            }

            result.body?.let { return it }

            val message = "Kall mot syfosoknad returnerer ikke data"
            log.error(message)
            throw RuntimeException(message)
        } catch (e: HttpClientErrorException.NotFound) {
            if (environmentToggles.isQ()) {
                return Nokkelinfo(Navn("$virksomhetsnummer sitt orgnavn (dev testdata)"))
            }
            throw e
        }
    }
}
