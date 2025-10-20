package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.sigrun.PensjongivendeInntektClientException
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.`should not be equal to`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

private const val FNR = "01017011111"
private const val SOKNAD_ID = "ca3f2ca6-7095-4124-855a-d4bafbbfe156"

class SigrunInnhentingsregelTest : FellesTestOppsett() {
    @BeforeEach
    fun resetMockWebServer() {
        with(SigrunMockDispatcher) {
            antallKall.set(0)
            clearQueue()
        }
    }

    @Test
    fun `Returnerer 3 siste år med inntekt`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(FNR, "2023")
            enqueueMockResponse(FNR, "2022")
            enqueueMockResponse(FNR, "2021")
        }

        val response = sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2024)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        response `should not be equal to` null
        response!!.size `should be equal to` 3
        response.let {
            it[0].pensjonsgivendeInntekt.first() `should not be` null
            it[0].inntektsaar `should be equal to` "2023"
            it[1].pensjonsgivendeInntekt.first() `should not be` null
            it[1].inntektsaar `should be equal to` "2022"
            it[2].pensjonsgivendeInntekt.first() `should not be` null
            it[2].inntektsaar `should be equal to` "2021"
        }
    }

    @Test
    fun `Første år har har ikke inntekt så hopper over det og henter de 3 neste`() {
        with(SigrunMockDispatcher) {
            enqueueResponse(sigrun404Feil())
            enqueueMockResponse(FNR, "2022")
            enqueueMockResponse(FNR, "2021")
            enqueueMockResponse(FNR, "2020")
        }

        val response = sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2024)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        response `should not be equal to` null
        response!!.size `should be equal to` 3
        response.let {
            it[0].pensjonsgivendeInntekt.first() `should not be` null
            it[0].inntektsaar `should be equal to` "2022"
            it[1].pensjonsgivendeInntekt.first() `should not be` null
            it[1].inntektsaar `should be equal to` "2021"
            it[2].pensjonsgivendeInntekt.first() `should not be` null
            it[2].inntektsaar `should be equal to` "2020"
        }
    }

    @Test
    fun `Første år er ugyldig så hopper over det og henter de 3 neste`() {
        with(SigrunMockDispatcher) {
            enqueueResponse(sigrun500Feil())
            enqueueMockResponse(FNR, "2023")
            enqueueMockResponse(FNR, "2022")
            enqueueMockResponse(FNR, "2021")
        }

        val response = sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2025)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        response `should not be equal to` null
        response!!.size `should be equal to` 3
        response.let {
            it[0].pensjonsgivendeInntekt.first() `should not be` null
            it[0].inntektsaar `should be equal to` "2023"
            it[1].pensjonsgivendeInntekt.first() `should not be` null
            it[1].inntektsaar `should be equal to` "2022"
            it[2].pensjonsgivendeInntekt.first() `should not be` null
            it[2].inntektsaar `should be equal to` "2021"
        }
    }

    @Test
    fun `Første år har ikke inntekt så hopper over det og henter de 3 neste, inkludert et år uten inntekt`() {
        with(SigrunMockDispatcher) {
            repeat(2) {
                enqueueResponse(sigrun404Feil())
            }
            enqueueMockResponse(FNR, "2021")
            enqueueMockResponse(FNR, "2020")
        }

        val response = sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2024)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        response `should not be equal to` null
        response!!.size `should be equal to` 3
        response.let {
            it[0].pensjonsgivendeInntekt `should be` emptyList()
            it[0].inntektsaar `should be equal to` "2022"
            it[1].pensjonsgivendeInntekt.first() `should not be` null
            it[1].inntektsaar `should be equal to` "2021"
            it[2].pensjonsgivendeInntekt.first() `should not be` null
            it[2].inntektsaar `should be equal to` "2020"
        }
    }

    @Test
    fun `Returnerer fjerde års inntekt hvis kun det finnes`() {
        with(SigrunMockDispatcher) {
            repeat(3) {
                enqueueResponse(sigrun404Feil())
            }
            enqueueMockResponse(FNR, "2020")
        }

        val response = sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2024)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4

        response `should not be equal to` null
        response!!.size `should be equal to` 3
        response.let {
            it[0].pensjonsgivendeInntekt `should be` emptyList()
            it[0].inntektsaar `should be equal to` "2022"
            it[1].pensjonsgivendeInntekt `should be` emptyList()
            it[1].inntektsaar `should be equal to` "2021"
            it[2].pensjonsgivendeInntekt.first() `should not be` null
            it[2].inntektsaar `should be equal to` "2020"
        }
    }

    @Test
    fun `Returnerer siste 3 år med inntekt selv om det mangler inntekt for det tredje`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(FNR, "2023")
            enqueueMockResponse(FNR, "2022")
            enqueueResponse(sigrun404Feil())
        }

        val response = sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2024)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3

        response `should not be equal to` null
        response!!.size `should be equal to` 3
        response.let {
            it[0].pensjonsgivendeInntekt.first() `should not be` null
            it[0].inntektsaar `should be equal to` "2023"
            it[1].pensjonsgivendeInntekt.first() `should not be` null
            it[1].inntektsaar `should be equal to` "2022"
            it[2].pensjonsgivendeInntekt `should be` emptyList()
            it[2].inntektsaar `should be equal to` "2021"
        }
    }

    @Test
    fun `Avbryter henting og returnerer null når tidligste år er før 2017`() {
        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2019) `should be` null
        SigrunMockDispatcher.antallKall.get() `should be equal to` 0
    }

    @Test
    fun `Avbryter henting og returnerer null når første år hoppes over og tidligste år etter det blir før 2017`() {
        with(SigrunMockDispatcher) {
            enqueueResponse(sigrun404Feil())
            enqueueMockResponse(FNR, "2019")
            enqueueMockResponse(FNR, "2018")
        }

        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2020) `should be` null
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3
    }

    @Test
    fun `Spring gjør ikke retry for PensjongivendeInntektClientException`() {
        sigrunMockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("{\"errorCode\": \"PGIF-007\", \"errorMessage\": \"Ikke treff på oppgitt personidentifikator.\"}"),
        )

        assertThrows<PensjongivendeInntektClientException> {
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2024)
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
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(fnr = FNR, SOKNAD_ID, 2024)
        }

        // @Retryable(maxAttempts = 3)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 3
    }

    @Test
    fun `Finnes pensjonsgivende inntekt for år`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(FNR, "2020")
        }

        val response = sykepengegrunnlagForNaeringsdrivende.finnesPensjonsgivendeInntektForAar(FNR, 2020)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 1

        response `should be equal to` true
    }

    @Test
    fun `Finnes ikke pensjonsgivende inntekt for år`() {
        with(SigrunMockDispatcher) {
            enqueueResponse(sigrun404Feil())
        }

        val response = sykepengegrunnlagForNaeringsdrivende.finnesPensjonsgivendeInntektForAar(FNR, 2020)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 1

        response `should be equal to` false
    }

    @Test
    fun `Finnes 0 pensjonsgivende inntekt for år`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(
                fnr = FNR,
                inntektsaar = "2020",
                inntekt =
                    listOf(
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2020-07-17",
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                        ),
                        PensjonsgivendeInntekt(
                            datoForFastsetting = "2020-07-19",
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvNaeringsinntekt = 0,
                        ),
                    ),
            )
        }

        val response = sykepengegrunnlagForNaeringsdrivende.finnesPensjonsgivendeInntektForAar(FNR, 2020)
        SigrunMockDispatcher.antallKall.get() `should be equal to` 1

        response `should be equal to` false
    }
}
