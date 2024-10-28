package no.nav.helse.flex.service

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepClient
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class GrunnbeloepService(private val grunnbeloepClient: GrunnbeloepClient) {
    // TODO: Trenger vi en egen service?
    @Cacheable("grunnbelop-historikk")
    fun hentHistorikk(from: LocalDate): List<GrunnbeloepResponse> {
        val hentForDato = LocalDate.of(from.year, 1, 1).minusYears(5)
        return grunnbeloepClient.getHistorikk(hentForDato).block() ?: emptyList()
    }
}
