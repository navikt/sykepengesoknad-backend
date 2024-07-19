package no.nav.helse.flex.client.gronnbeloep

import org.springframework.beans.factory.annotation.Value
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
        val formatertDato = dato?.format(DateTimeFormatter.ISO_DATE)
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/grunnbeloep")
                    .queryParam("dato", formatertDato)
                    .build()
            }
            .retrieve()
            .bodyToMono(GrunnbeloepResponse::class.java)
    }

    fun getHistorikk(fra: LocalDate?): Mono<List<GrunnbeloepResponse>> {
        val formatertDato = fra?.format(DateTimeFormatter.ISO_DATE)
        return webClient.get()
            .uri { uriBuilder ->
                uriBuilder.path("/historikk")
                    .queryParam("fra", formatertDato)
                    .build()
            }
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
