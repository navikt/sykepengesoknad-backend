package no.nav.helse.flex.service

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepClient
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class GrunnbeloepService(private val grunnbeloepClient: GrunnbeloepClient) {
    // TODO: Returner HashMap sånn at vi slipper å lete etter riktig år for grunnbeloepPaaSykmeldingstidspunkt.
    @Cacheable("grunnbelop-historikk")
    fun hentGrunnbeloepHistorikk(year: Int): List<GrunnbeloepResponse> {
        val hentForDato = LocalDate.of(year, 1, 1).minusYears(5)
        return grunnbeloepClient.hentGrunnbeloepHistorikk(hentForDato).block().takeIf { !it.isNullOrEmpty() }
            ?: throw RuntimeException("Fant ikke grunnbeløphistorikk for $hentForDato.")
    }
}
