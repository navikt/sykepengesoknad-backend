package no.nav.syfo.client.sykmelding

import no.nav.syfo.domain.exception.ManglerSykmeldingException
import no.nav.syfo.domain.exception.RestFeilerException
import no.nav.syfo.logger
import no.nav.syfo.model.sykmelding.arbeidsgiver.ArbeidsgiverSykmelding
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod.GET
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.OK
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class SyfoSmRegisterClient(
    @Value("\${syfosmregister.url}") private val url: String,
    private val syfosmregisterRestTemplate: RestTemplate,
) {

    val log = logger()

    @Retryable
    fun hentSykmelding(sykmeldingID: String): ArbeidsgiverSykmelding? {
        val uriBuilder = UriComponentsBuilder.fromHttpUrl("$url/api/v2/sykmelding/$sykmeldingID")

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON

        val result = try {
            syfosmregisterRestTemplate
                .exchange(
                    uriBuilder.toUriString(),
                    GET,
                    HttpEntity<Any>(headers),
                    ArbeidsgiverSykmelding::class.java
                )
        } catch (e: HttpClientErrorException) {
            if (e.statusCode == NOT_FOUND) {
                return null
            }
            log.error("SMRegister oppslag feiler ", e)
            throw RestFeilerException()
        }

        if (result.statusCode != OK) {
            val message = "Kall mot smregister feiler med HTTP-" + result.statusCode
            log.error(message)
            throw RestFeilerException()
        }

        result.body?.let { return it }

        val message = "Kall mot smregister returnerer ikke data"
        log.error(message)
        throw ManglerSykmeldingException()
    }
}
