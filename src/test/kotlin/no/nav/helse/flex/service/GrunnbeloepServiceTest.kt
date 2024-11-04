package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepClient
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.SpyBean
import org.springframework.cache.CacheManager
import java.time.LocalDate

class GrunnbeloepServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var cacheManager: CacheManager

    @SpyBean
    lateinit var grunnbeloepClient: GrunnbeloepClient

    @AfterEach
    fun clearCache() {
        cacheManager.getCache("grunnbelop-historikk")?.clear()
    }

    @Test
    fun `Historikk hentes fra cache etter første kall med samme år`() {
        val hentForDato = LocalDate.of(2024, 1, 1)

        val forste = grunnbeloepService.hentGrunnbeloepHistorikk(hentForDato.year)
        val andre = grunnbeloepService.hentGrunnbeloepHistorikk(hentForDato.year)

        verify(grunnbeloepClient, times(1)).hentGrunnbeloepHistorikk(hentForDato.minusYears(5))

        forste.size `should be equal to` 6
        andre.size `should be equal to` 6

        // Sikrer at deserialisering av cache-verdi fungerer.
        forste.first().grunnbeløp `should be equal to` 99858
        andre.first().grunnbeløp `should be equal to` 99858
    }

    @Test
    fun `Historikk hentes ikke fra cache for to forskjellige år`() {
        val forsteDato = LocalDate.of(2024, 1, 1)
        val andreDato = LocalDate.of(2018, 1, 1)

        val forste = grunnbeloepService.hentGrunnbeloepHistorikk(forsteDato.year)
        val andre = grunnbeloepService.hentGrunnbeloepHistorikk(andreDato.year)

        verify(grunnbeloepClient, times(1)).hentGrunnbeloepHistorikk(forsteDato.minusYears(5))
        verify(grunnbeloepClient, times(1)).hentGrunnbeloepHistorikk(andreDato.minusYears(5))

        forste.size `should be equal to` 6
        andre.size `should be equal to` 12

        forste.first().grunnbeløp `should be equal to` 99858
        andre.first().grunnbeløp `should be equal to` 85245
    }

    @Test
    fun `Kaster exception hvis det ikke returneres noe resultat`() {
        // Mock har ikke verdier for 2023.
        val forsteDato = LocalDate.of(2023, 1, 1)
        assertThrows<RuntimeException> { grunnbeloepService.hentGrunnbeloepHistorikk(forsteDato.year) }
    }
}
