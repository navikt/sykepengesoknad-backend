package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.LocalDate

class GrunnbeloepServiceTest : FellesTestOppsett() {
    @Test
    fun `Historikk hentes fra cache etter første kall`() {
        val fraDato = LocalDate.of(2024, 1, 1)

        val forste = grunnbeloepService.hentHistorikk(fraDato)
        val andre = grunnbeloepService.hentHistorikk(fraDato)

        verify(grunnbeloepClient, times(1))
            .getHistorikk(LocalDate.of(fraDato.year, 1, 1).minusYears(5))

        forste.size `should be equal to` 6
        andre.size `should be equal to` 6

        // Sikrer at deserialisering har fungert.
        forste.first().grunnbeløp `should be equal to` 99858
        andre.first().grunnbeløp `should be equal to` 99858
    }
}
