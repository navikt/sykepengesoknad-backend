package no.nav.helse.flex.client.grunnbeloep

import no.nav.helse.flex.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.util.retry.Retry
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class GrunnbeloepClient(
    webClientBuilder: WebClient.Builder,
    @Value("\${GRUNNBELOEP_API_URL}") private val url: String,
) {
    val log = logger()

    // TODO: Bytt ut med RestTemplate
    private val webClient = webClientBuilder.baseUrl(url).build()

    fun getHistorikk(fra: LocalDate?): Mono<List<GrunnbeloepResponse>> {
        val historikk =
            webClient.get()
                .uri { uriBuilder ->
                    val builder = uriBuilder.path("/historikk/grunnbeløp")
                    if (fra != null) {
                        val formatertDato = fra.format(DateTimeFormatter.ISO_DATE)
                        builder.queryParam("fra", formatertDato)
                    }
                    builder.build()
                }
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .onStatus({ status -> status.is4xxClientError || status.is5xxServerError }) { response ->
                    response.createException()
                        .flatMap { Mono.error(RuntimeException("Feil ved henting av grunnbeløphistorikk: ${response.statusCode()}")) }
                }
                .bodyToFlux(GrunnbeloepResponse::class.java)
                .collectList()
                .retryWhen(Retry.backoff(3, Duration.ofSeconds(2)))
                .onErrorResume { error ->
                    log.error("Feil ved henting av grunnbeløphistorikk fra $fra til i dag", error)
                    Mono.just(emptyList<GrunnbeloepResponse>())
                }

        log.info("Hentet grunnbeløphistorikk fra $fra til i dag.")
        return historikk
    }
}

data class GrunnbeloepResponse(
    val dato: String,
    val grunnbeløp: Int,
    val grunnbeløpPerMaaned: Int,
    val gjennomsnittPerÅr: Int,
    val omregningsfaktor: Float,
    val virkningstidspunktForMinsteinntekt: String,
)
