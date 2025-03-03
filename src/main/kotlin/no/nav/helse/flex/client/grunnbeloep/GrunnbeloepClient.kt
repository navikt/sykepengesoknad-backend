package no.nav.helse.flex.client.grunnbeloep

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.util.objectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.io.Serializable
import java.time.Duration
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Component
class GrunnbeloepClient(
    @Value("\${GRUNNBELOEP_API_URL}")
    private val url: String,
    restClientBuilder: RestClient.Builder,
) {
    val restClient =
        restClientBuilder.baseUrl(url).requestFactory(
            HttpComponentsClientHttpRequestFactory().also {
                it.setReadTimeout(Duration.of(10, ChronoUnit.SECONDS))
            },
        ).build()

    @Retryable(
        value = [HttpServerErrorException::class, HttpClientErrorException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000),
    )
    fun hentGrunnbeloepHistorikk(hentForDato: LocalDate): List<GrunnbeloepResponse> {
        return restClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/historikk/grunnbeløp")
                    .queryParam("fra", hentForDato)
                    .build()
            }
            .retrieve()
            .body(String::class.java)!!.tilGrunnbeloepHistorikk()
    }

    private fun String.tilGrunnbeloepHistorikk(): List<GrunnbeloepResponse> {
        return objectMapper.readValue(this)
    }
}

data class GrunnbeloepResponse(
    val dato: String,
    val grunnbeløp: Int,
    val gjennomsnittPerÅr: Int,
) : Serializable
