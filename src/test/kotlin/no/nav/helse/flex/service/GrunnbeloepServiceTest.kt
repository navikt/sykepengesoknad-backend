package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.time.LocalDate

class GrunnbeloepServiceTest : FellesTestOppsett() {
    @Test
    fun `skal bruke cache etter første kall`() {
        val fraDato = LocalDate.of(2024, 1, 1)

        grunnbeloepService.hentHistorikk(fraDato)
        grunnbeloepService.hentHistorikk(fraDato)
        grunnbeloepService.hentHistorikk(fraDato)
        grunnbeloepService.hentHistorikk(fraDato)

        verify(grunnbeloepClient, times(1))
            .getHistorikk(LocalDate.of(fraDato.year, 1, 1).minusYears(5))
    }
}
