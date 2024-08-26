package no.nav.helse.flex.client.grunnbeloep

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class GrunnbeloepClient(
    webClientBuilder: WebClient.Builder,
    @Value("\${GRUNNBELOEP_API_URL}") private val url: String,
) {
    private val webClient = webClientBuilder.baseUrl(url).build()

    fun getGrunnbeloep(dato: LocalDate?): Mono<GrunnbeloepResponse> {
        return webClient.get()
            .uri { uriBuilder ->
                val builder = uriBuilder.path("/grunnbeloep")
                if (dato != null) {
                    val formatertDato = dato.format(DateTimeFormatter.ISO_DATE)
                    builder.queryParam("dato", formatertDato)
                }
                builder.build()
            }
            .retrieve()
            .bodyToMono(GrunnbeloepResponse::class.java)
    }

    fun getHistorikk(fra: LocalDate?): Mono<List<GrunnbeloepResponse>> {
        return webClient.get()
            .uri { uriBuilder ->
                val builder = uriBuilder.path("/historikk")
                if (fra != null) {
                    val formatertDato = fra.format(DateTimeFormatter.ISO_DATE)
                    builder.queryParam("fra", formatertDato)
                }
                builder.build()
            }
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .retrieve()
            .bodyToFlux(GrunnbeloepResponse::class.java)
            .collectList()
    }
}

data class GrunnbeloepResponse(
    val dato: String,
    val grunnbeloep: Int,
    val grunnbeloepPerMaaned: Int,
    val gjennomsnittPerAar: Int,
    val omregningsfaktor: Float,
    val virkningstidspunktForMinsteinntekt: String,
)
