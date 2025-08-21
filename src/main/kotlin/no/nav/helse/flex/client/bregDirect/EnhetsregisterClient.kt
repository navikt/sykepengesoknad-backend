package no.nav.helse.flex.client.bregDirect

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Duration

@Component
class EnhetsregisterClient(
    private val webClientBuilder: WebClient.Builder,
    @Value("\${ENHETSREGISTER_BASE_URL:https://data.brreg.no/enhetsregisteret}")
    private val baseUrl: String
) {
    private val webClient: WebClient = webClientBuilder
        .baseUrl(baseUrl)
        .build()

    suspend fun hentEnhet(orgnummer: String): String =
        try {
            hentEnhetFraEndpoint("/api/enheter/$orgnummer")
        } catch (e: NotFoundException) {
            hentEnhetFraEndpoint("/api/underenheter/$orgnummer")
        }

    private suspend fun hentEnhetFraEndpoint(path: String): String {
        return webClient.get()
            .uri(path)
            .retrieve()
            .onStatus({ it == HttpStatus.NOT_FOUND }) {
                Mono.error(NotFoundException())
            }
            .bodyToMono(String::class.java)
            .timeout(Duration.ofSeconds(15))
            .block(Duration.ofSeconds(15))
            ?: throw NotFoundException()
    }

    class NotFoundException : RuntimeException()
}
