package no.nav.helse.flex.service

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mockdispatcher.grunnbeloep.GrunnbeloepApiMockDispatcher
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.CacheManager
import org.springframework.test.context.TestPropertySource
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.time.LocalDate

@TestPropertySource(properties = ["GRUNNBELOEP_RETRY_ATTEMPTS=1"])
class GrunnbeloepServiceTest : FellesTestOppsett() {
    @Autowired
    private lateinit var cacheManager: CacheManager

    @BeforeEach
    fun reset() {
        cacheManager.getCache("grunnbeloep-historikk")?.clear()
        GrunnbeloepApiMockDispatcher.clearQueue()
    }

    @Test
    fun `Historikk henter for 5 + 1 år tilbake i tid`() {
        // Henter historikk for 2018, 2019, 2020, 2021, 2022 og 2023.
        val hentForDato = LocalDate.of(2024, 1, 1)
        val response = grunnbeloepService.hentGrunnbeloepHistorikk(hentForDato.year)

        verify(grunnbeloepClient, times(1)).hentGrunnbeloepHistorikk(hentForDato.minusYears(5))
        response.size `should be equal to` 7
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
        GrunnbeloepApiMockDispatcher.enqueueResponse(MockResponse().setResponseCode(404))

        val forsteDato = LocalDate.of(1970, 1, 1)
        invoking { grunnbeloepService.hentGrunnbeloepHistorikk(forsteDato.year) }
            .shouldThrow(HttpClientErrorException.NotFound::class)
    }

    @Test
    fun `Kaster før 1967`() {
        GrunnbeloepApiMockDispatcher.enqueueResponse(MockResponse().setResponseCode(500))

        val forsteDato = LocalDate.of(1966, 1, 1)
        invoking { grunnbeloepService.hentGrunnbeloepHistorikk(forsteDato.year) }
            .shouldThrow(HttpServerErrorException.InternalServerError::class)
    }
}
