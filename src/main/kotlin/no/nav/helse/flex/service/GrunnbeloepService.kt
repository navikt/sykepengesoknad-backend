package no.nav.helse.flex.service

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepClient
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class GrunnbeloepService(private val grunnbeloepClient: GrunnbeloepClient) {
    @Cacheable("grunnbeloep-historikk")
    fun hentGrunnbeloepHistorikk(year: Int): Map<Int, GrunnbeloepResponse> {
        val hentForDato = LocalDate.of(year, 1, 1).minusYears(5)
        val hentGrunnbeloepHistorikk =
            grunnbeloepClient.hentGrunnbeloepHistorikk(hentForDato).block().takeIf { !it.isNullOrEmpty() }
                ?: throw RuntimeException("Fant ikke grunnbeløphistorikk for $hentForDato.")
        return hentGrunnbeloepHistorikk.associateBy { LocalDate.parse(it.dato).year }
    }
}
