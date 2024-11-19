package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import java.time.LocalDate

class GrunnbeloepServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var cacheManager: CacheManager

    @BeforeEach
    fun clearCache() {
        cacheManager.getCache("grunnbeloep-historikk")?.clear()
    }

    @Disabled
    @Test
    fun `Historikk hentes fra cache etter første kall med samme år`() {
        val hentForDato = LocalDate.of(2024, 1, 1)

        val forsteReponse = grunnbeloepService.hentGrunnbeloepHistorikk(hentForDato.year)
        val andreResponse = grunnbeloepService.hentGrunnbeloepHistorikk(hentForDato.year)

        verify(grunnbeloepClient, times(1)).hentGrunnbeloepHistorikk(hentForDato.minusYears(5))

        forsteReponse.size `should be equal to` 6
        andreResponse.size `should be equal to` 6

        // Sikrer at deserialisering av cache-verdi fungerer.
        forsteReponse[2019]?.grunnbeløp `should be equal to` 99858
        andreResponse[2019]?.grunnbeløp `should be equal to` 99858
    }

    @Test
    fun `Historikk hentes ikke fra cache for to forskjellige år`() {
        val forsteDato = LocalDate.of(2024, 1, 1)
        val andreDato = LocalDate.of(2018, 1, 1)

        val forsteResponse = grunnbeloepService.hentGrunnbeloepHistorikk(forsteDato.year)
        val andreResponse = grunnbeloepService.hentGrunnbeloepHistorikk(andreDato.year)

        verify(grunnbeloepClient, times(1)).hentGrunnbeloepHistorikk(forsteDato.minusYears(5))
        verify(grunnbeloepClient, times(1)).hentGrunnbeloepHistorikk(andreDato.minusYears(5))

        forsteResponse.size `should be equal to` 6
        andreResponse.size `should be equal to` 13

        forsteResponse[2019]?.grunnbeløp `should be equal to` 99858
        andreResponse[2013]?.grunnbeløp `should be equal to` 85245
    }

    @Test
    fun `Kaster exception hvis det ikke returneres noe resultat`() {
        // Mock har ikke verdier for 2017, som er 5 år tilbake i tid for 2022.
        val forsteDato = LocalDate.of(2022, 1, 1)
        assertThrows<RuntimeException> { grunnbeloepService.hentGrunnbeloepHistorikk(forsteDato.year) }
    }
}
