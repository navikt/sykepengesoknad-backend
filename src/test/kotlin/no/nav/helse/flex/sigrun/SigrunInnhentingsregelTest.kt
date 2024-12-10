package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.sigrun.PensjongivendeInntektClientException
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.lag404MockResponse
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher.lagMockResponse
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val FNR = "12345678910"

class SigrunInnhentingsregelTest : FellesTestOppsett() {
    @BeforeEach
    fun resetMockWebServer() {
        SigrunMockDispatcher.antallKall.set(0)
    }

    @Test
    fun `Returnerer 3 siste år med inntekt`() {
        with(sigrunMockWebServer) {
            enqueue(lagMockResponse(FNR, "2023"))
            enqueue(lagMockResponse(FNR, "2022"))
            enqueue(lagMockResponse(FNR, "2021"))
        }

        val response = sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, 2024)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        response?.size `should be equal to` 3
        response?.let {
            it[0].pensjonsgivendeInntekt `should not be` null
            it[0].inntektsaar `should be equal to` "2023"
            it[1].pensjonsgivendeInntekt `should not be` null
            it[1].inntektsaar `should be equal to` "2022"
            it[2].pensjonsgivendeInntekt `should not be` null
            it[2].inntektsaar `should be equal to` "2021"
        }
    }

    @Test
    fun `Første har har ikke inntekt så hopper over det og henter de 3 neste`() {
        with(sigrunMockWebServer) {
            enqueue(lag404MockResponse())
            enqueue(lagMockResponse(FNR, "2022"))
            enqueue(lagMockResponse(FNR, "2021"))
            enqueue(lagMockResponse(FNR, "2020"))
        }

        val response = sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, 2024)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        response?.size `should be equal to` 3
        response?.let {
            it[0].pensjonsgivendeInntekt `should not be` null
            it[0].inntektsaar `should be equal to` "2022"
            it[1].pensjonsgivendeInntekt `should not be` null
            it[1].inntektsaar `should be equal to` "2021"
            it[2].pensjonsgivendeInntekt `should not be` null
            it[2].inntektsaar `should be equal to` "2020"
        }
    }

    @Test
    fun `Returnerer null når to første år ikke har inntekt`() {
        with(sigrunMockWebServer) {
            repeat(2) {
                enqueue(lag404MockResponse())
            }
            enqueue(lagMockResponse(FNR, "2021"))
            enqueue(lagMockResponse(FNR, "2020"))
        }

        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, 2024) `should be` null
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4
    }

    @Test
    fun `Returnerer null når kun fjerde år har inntekt`() {
        with(sigrunMockWebServer) {
            repeat(3) {
                enqueue(lag404MockResponse())
            }
            enqueue(lagMockResponse(FNR, "2020"))
        }

        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, 2024) `should be` null
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4
    }

    @Test
    fun `Returnerer null og henter ikke fjerde år to første år mangler inntekt`() {
        with(sigrunMockWebServer) {
            enqueue(lagMockResponse(FNR, "2023"))
            enqueue(lag404MockResponse())
            enqueue(lagMockResponse(FNR, "2021"))
        }

        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, 2024) `should be` null
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3
    }

    @Test
    fun `Returnerer null og hener ikke fjerde år når tredje år mangler inntekt`() {
        with(sigrunMockWebServer) {
            enqueue(lagMockResponse(FNR, "2023"))
            enqueue(lagMockResponse(FNR, "2022"))
            enqueue(lag404MockResponse())
        }

        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, 2024) `should be` null
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3
    }

    @Test
    fun `Returnerer null når første og fjerde år mangler inntekt`() {
        with(sigrunMockWebServer) {
            enqueue(lag404MockResponse())
            enqueue(lagMockResponse(FNR, "2022"))
            enqueue(lagMockResponse(FNR, "2021"))
            enqueue(lag404MockResponse())
        }

        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, 2024) `should be` null
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4
    }

    @Test
    fun `Spring gjør ikke retry for PensjongivendeInntektClientException`() {
        sigrunMockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("{\"errorCode\": \"PGIF-007\", \"errorMessage\": \"Ikke treff på oppgitt personidentifikator.\"}"),
        )

        assertThrows<PensjongivendeInntektClientException> {
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = FNR,
                sykmeldtAar = 2024,
            )
        }

        SigrunMockDispatcher.antallKall.get() `should be equal to` 1
    }

    @Test
    fun `Spring gjør retry når det kastes en annen exception enn PensjongivendeInntektClientException`() {
        repeat(3) {
            sigrunMockWebServer.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .addHeader("Content-Type", "application/json"),
            )
        }

        assertThrows<RuntimeException> {
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(
                fnr = FNR,
                sykmeldtAar = 2024,
            )
        }

        // @Retryable(maxAttempts = 3)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3
    }
}
