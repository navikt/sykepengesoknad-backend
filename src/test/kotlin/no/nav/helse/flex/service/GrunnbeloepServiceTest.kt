package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepClient
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import java.time.LocalDate

class GrunnbeloepServiceTest : FellesTestOppsett() {
    @Autowired
    lateinit var grunnbeloepService: GrunnbeloepService

    @SpyBean
    private lateinit var grunnbeloepClient: GrunnbeloepClient

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
