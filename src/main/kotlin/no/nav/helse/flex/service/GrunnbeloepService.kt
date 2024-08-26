package no.nav.helse.flex.service

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepClient
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import org.springframework.cache.annotation.CacheConfig
import org.springframework.stereotype.Service
import reactor.core.publisher.Mono
import java.time.LocalDate

@Service
@CacheConfig
class GrunnbeloepService(private val grunnbeloepClient: GrunnbeloepClient) {
    fun getGrunnbeloep(dato: LocalDate?): Mono<GrunnbeloepResponse> {
        return grunnbeloepClient.getGrunnbeloep(dato)
    }

    fun getHistorikk(fra: LocalDate?): Mono<List<GrunnbeloepResponse>> {
        return grunnbeloepClient.getHistorikk(fra)
    }

    fun hentGrunnbeloepForFemAarSiden(): Mono<List<GrunnbeloepResponse>> {
        val detteAaret = LocalDate.now().year
        val sisteFemAar =
            (detteAaret - 5 until detteAaret).map {
                getGrunnbeloep(LocalDate.of(it, 1, 1))
            }

        return Mono.zip(sisteFemAar) { results ->
            results.map { it as GrunnbeloepResponse }
        }
    }

    // TODO: Cache
    fun hentHistorikkSisteFemAar(): Mono<List<GrunnbeloepResponse>> {
        val femAarSiden = LocalDate.of(2024, 1, 1).minusYears(5)
        return getHistorikk(femAarSiden)
    }
}
