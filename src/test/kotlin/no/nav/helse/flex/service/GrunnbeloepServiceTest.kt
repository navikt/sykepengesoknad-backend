package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import java.time.LocalDate

class GrunnbeloepServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var cacheManager: CacheManager

    @AfterEach
    fun clearCache() {
        cacheManager.getCache("grunnbelop-historikk")?.clear()
    }

    @Test
    fun `Historikk hentes fra cache etter første kall for samme dato`() {
        val fraDato = LocalDate.of(2024, 1, 1)

        val forste = grunnbeloepService.hentHistorikk(fraDato)
        val andre = grunnbeloepService.hentHistorikk(fraDato)

        verify(grunnbeloepClient, times(1))
            .getHistorikk(LocalDate.of(fraDato.year, 1, 1).minusYears(5))

        forste.size `should be equal to` 6
        andre.size `should be equal to` 6

        // Sikrer at deserialisering av cached verdi fungerer.
        forste.first().grunnbeløp `should be equal to` 99858
        andre.first().grunnbeløp `should be equal to` 99858
    }

    @Test
    fun `Historikk hentes fra cache etter første kall for forskjellige dato samme år`() {
        val fraDato = LocalDate.of(2024, 1, 1)

        grunnbeloepService.hentHistorikk(fraDato)
        grunnbeloepService.hentHistorikk(fraDato.plusDays(1))

        verify(grunnbeloepClient, times(1))
            .getHistorikk(LocalDate.of(fraDato.year, 1, 1).minusYears(5))
    }
}
