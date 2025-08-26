package no.nav.helse.flex.client.grunnbeloep

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.util.objectMapper
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import java.io.Serializable
import java.time.LocalDate

@Component
class GrunnbeloepClient(
    @param:Value("\${GRUNNBELOEP_API_URL}")
    private val url: String,
    @param:Qualifier("grunnbelopRestClient")
    private val restClient: RestClient,
) {
    @Retryable(
        value = [HttpServerErrorException::class, HttpClientErrorException::class],
        maxAttempts = 3,
        backoff = Backoff(delay = 1000),
    )
    fun hentGrunnbeloepHistorikk(hentForDato: LocalDate): List<GrunnbeloepResponse> =
        restClient
            .get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/historikk/grunnbeløp")
                    .queryParam("fra", hentForDato)
                    .build()
            }.retrieve()
            .body(String::class.java)!!
            .tilGrunnbeloepHistorikk()

    private fun String.tilGrunnbeloepHistorikk(): List<GrunnbeloepResponse> = objectMapper.readValue(this)
}

data class GrunnbeloepResponse(
    val dato: String,
    val grunnbeløp: Int,
    val gjennomsnittPerÅr: Int,
) : Serializable
