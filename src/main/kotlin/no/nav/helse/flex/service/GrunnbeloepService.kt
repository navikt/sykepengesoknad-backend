package no.nav.helse.flex.service

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepClient
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class GrunnbeloepService(
    private val grunnbeloepClient: GrunnbeloepClient,
) {
    fun hentGrunnbeloepHistorikk(year: Int): Map<Int, GrunnbeloepResponse> {
        // Siden vi bruker 1. januar som dato, vil vi også få med grunnbeløpet for året før 5 år tilbake
        val hentForDato = LocalDate.of(year, 1, 1).minusYears(5)
        return grunnbeloepClient.hentGrunnbeloepHistorikk(hentForDato).associateBy { LocalDate.parse(it.dato).year }
    }
}
