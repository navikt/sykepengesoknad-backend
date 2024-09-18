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

    fun getHistorikk(fra: LocalDate?): Mono<List<GrunnbeloepResponse>> {
        return grunnbeloepClient.getHistorikk(fra)
    }

    // TODO: Cache
    fun hentHistorikkSisteFemAar(): Mono<List<GrunnbeloepResponse>> {
        val femAarSiden = LocalDate.now().minusYears(5)
        return getHistorikk(femAarSiden)
    }
}
