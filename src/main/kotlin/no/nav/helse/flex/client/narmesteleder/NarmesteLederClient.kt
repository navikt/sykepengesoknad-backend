package no.nav.helse.flex.client.narmesteleder

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class NarmesteLederClient(
    @Value("\${narmesteleder.url}")
    private val narmestelederUrl: String,
    private val narmestelederRestTemplate: RestTemplate,
) {

    val log = logger()

    @Cacheable(cacheNames = ["flex-forskuttering-narmesteleder"])
    @Retryable
    fun arbeidsgiverForskutterer(sykmeldtFnr: String, orgnummer: String): Forskuttering {
        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_JSON
        headers["Sykmeldt-Fnr"] = sykmeldtFnr
        val uriString = UriComponentsBuilder.fromHttpUrl("$narmestelederUrl/arbeidsgiver/forskutterer")
            .queryParam("orgnummer", orgnummer)
            .toUriString()
        val result = narmestelederRestTemplate
            .exchange(
                uriString, HttpMethod.GET,
                HttpEntity<Any>(headers),
                ForskutteringRespons::class.java
            )
        if (result.statusCode != HttpStatus.OK) {
            val message = "Kall mot narmesteleder feiler med HTTP-" + result.statusCode
            log.error(message)
            throw RuntimeException(message)
        }

        result.body?.let { return it.forskuttering }

        val message = "Kall mot narmesteleder returnerer ikke data"
        throw RuntimeException(message)
    }
}

class ForskutteringRespons(val forskuttering: Forskuttering)

enum class Forskuttering {
    JA, NEI, UKJENT
}
