package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.BeforeEach
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

    @Test
    fun `Historikk henter for 5 + 1 år tilbake i tid`() {
        // Henter historikk for 2018, 2019, 2020, 2021, 2022 og 2023.
        val hentForDato = LocalDate.of(2024, 1, 1)
        val response = grunnbeloepService.hentGrunnbeloepHistorikk(hentForDato.year)

        verify(grunnbeloepClient, times(1)).hentGrunnbeloepHistorikk(hentForDato.minusYears(5))
        response.size `should be equal to` 6
        response[2019]?.grunnbeløp `should be equal to` 99858
    }

    @Test
    fun `Historikk returneres med null-verdi vi ikke trenger`() {
        // Henter historikk for 2012, 2013, 2014, 2015, 2016 og 2017
        // Grunnbeløp for 2012 mangler feltet virkningstidspunktForMinsteinntekt.
        val hentForDato = LocalDate.of(2018, 1, 1)
        val response = grunnbeloepService.hentGrunnbeloepHistorikk(hentForDato.year)

        verify(grunnbeloepClient, times(1)).hentGrunnbeloepHistorikk(hentForDato.minusYears(5))
        response.size `should be equal to` 13
        response[2019]?.grunnbeløp `should be equal to` 99858
        response[2012]?.grunnbeløp `should be equal to` 82122
    }

    @Test
    fun `Kaster exception hvis det ikke returneres noe resultat`() {
        // Mock har ikke verdier for 2017, som er 5 år tilbake i tid for 2022.
        val forsteDato = LocalDate.of(2022, 1, 1)
        assertThrows<RuntimeException> { grunnbeloepService.hentGrunnbeloepHistorikk(forsteDato.year) }
    }
}
