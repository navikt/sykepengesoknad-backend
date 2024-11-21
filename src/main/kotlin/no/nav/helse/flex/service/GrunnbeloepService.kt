package no.nav.helse.flex.service

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepClient
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class GrunnbeloepService(private val grunnbeloepClient: GrunnbeloepClient) {
    fun hentGrunnbeloepHistorikk(year: Int): Map<Int, GrunnbeloepResponse> {
        // Siden vi bruker 1. januar som dato, vil vi også få med grunnbeløpet for året før året 5 år tilbake hvis
        // datoen vi gjør spørringen er før 5. mai (datoen nytt grunnbeløp settes). Det vil si at når vi henter
        // grunnbeløpet for 2024 spør vi etter grunnbeløpet fra og med 2019, men vil også
        // få med grunnbeløpet for 2018. Etter 5. mai 2024 vil vi får fra og med 2019.
        val hentForDato = LocalDate.of(year, 1, 1).minusYears(5)
        val hentGrunnbeloepHistorikk =
            grunnbeloepClient.hentGrunnbeloepHistorikk(hentForDato).block().takeIf { !it.isNullOrEmpty() }
                ?: throw RuntimeException("Fant ikke grunnbeløphistorikk for $hentForDato.")
        return hentGrunnbeloepHistorikk.associateBy { LocalDate.parse(it.dato).year }
    }
}
