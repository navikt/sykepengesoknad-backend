package no.nav.helse.flex.sigrun

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import mockwebserver3.MockResponse
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.sigrun.PensjongivendeInntektClientException
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

private const val FNR = "01017011111"
private const val SOKNAD_ID = "ca3f2ca6-7095-4124-855a-d4bafbbfe156"

class SigrunInnhentingsregelTest : FellesTestOppsett() {
    @BeforeEach
    fun resetMockWebServer() {
        with(SigrunMockDispatcher) {
            antallKall.set(0)
            clear()
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
        SigrunMockDispatcher.antallKall() shouldBe 3

        response shouldNotBe null
        response!!.size shouldBe 3
        response.let {
            it[0].pensjonsgivendeInntekt.first() shouldNotBe null
            it[0].inntektsaar shouldBe "2023"
            it[1].pensjonsgivendeInntekt.first() shouldNotBe null
            it[1].inntektsaar shouldBe "2022"
            it[2].pensjonsgivendeInntekt.first() shouldNotBe null
            it[2].inntektsaar shouldBe "2021"
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
        SigrunMockDispatcher.antallKall() shouldBe 4

        response shouldNotBe null
        response!!.size shouldBe 3
        response.let {
            it[0].pensjonsgivendeInntekt.first() shouldNotBe null
            it[0].inntektsaar shouldBe "2022"
            it[1].pensjonsgivendeInntekt.first() shouldNotBe null
            it[1].inntektsaar shouldBe "2021"
            it[2].pensjonsgivendeInntekt.first() shouldNotBe null
            it[2].inntektsaar shouldBe "2020"
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
        SigrunMockDispatcher.antallKall() shouldBe 4

        response shouldNotBe null
        response!!.size shouldBe 3
        response.let {
            it[0].pensjonsgivendeInntekt.first() shouldNotBe null
            it[0].inntektsaar shouldBe "2023"
            it[1].pensjonsgivendeInntekt.first() shouldNotBe null
            it[1].inntektsaar shouldBe "2022"
            it[2].pensjonsgivendeInntekt.first() shouldNotBe null
            it[2].inntektsaar shouldBe "2021"
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
        SigrunMockDispatcher.antallKall() shouldBe 4

        response shouldNotBe null
        response!!.size shouldBe 3
        response.let {
            it[0].pensjonsgivendeInntekt shouldBe emptyList()
            it[0].inntektsaar shouldBe "2022"
            it[1].pensjonsgivendeInntekt.first() shouldNotBe null
            it[1].inntektsaar shouldBe "2021"
            it[2].pensjonsgivendeInntekt.first() shouldNotBe null
            it[2].inntektsaar shouldBe "2020"
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
        SigrunMockDispatcher.antallKall() shouldBe 4

        response shouldNotBe null
        response!!.size shouldBe 3
        response.let {
            it[0].pensjonsgivendeInntekt shouldBe emptyList()
            it[0].inntektsaar shouldBe "2022"
            it[1].pensjonsgivendeInntekt shouldBe emptyList()
            it[1].inntektsaar shouldBe "2021"
            it[2].pensjonsgivendeInntekt.first() shouldNotBe null
            it[2].inntektsaar shouldBe "2020"
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
        SigrunMockDispatcher.antallKall() shouldBe 3

        response shouldNotBe null
        response!!.size shouldBe 3
        response.let {
            it[0].pensjonsgivendeInntekt.first() shouldNotBe null
            it[0].inntektsaar shouldBe "2023"
            it[1].pensjonsgivendeInntekt.first() shouldNotBe null
            it[1].inntektsaar shouldBe "2022"
            it[2].pensjonsgivendeInntekt shouldBe emptyList()
            it[2].inntektsaar shouldBe "2021"
        }
    }

    @Test
    fun `Avbryter henting og returnerer null når tidligste år er før 2017`() {
        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2019) shouldBe null
        SigrunMockDispatcher.antallKall() shouldBe 0
    }

    @Test
    fun `Avbryter henting og returnerer null når første år hoppes over og tidligste år etter det blir før 2017`() {
        with(SigrunMockDispatcher) {
            enqueueResponse(sigrun404Feil())
            enqueueMockResponse(FNR, "2019")
            enqueueMockResponse(FNR, "2018")
        }

        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2020) shouldBe null
        SigrunMockDispatcher.antallKall() shouldBe 3
    }

    @Test
    fun `Spring gjør ikke retry for PensjongivendeInntektClientException`() {
        SigrunMockDispatcher.enqueueResponse(
            MockResponse(
                code = 404,
                body = "{\"errorCode\": \"PGIF-007\", \"errorMessage\": \"Ikke treff på oppgitt personidentifikator.\"}",
            ),
        )

        assertThrows<PensjongivendeInntektClientException> {
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, SOKNAD_ID, 2024)
        }

        SigrunMockDispatcher.antallKall() shouldBe 1
    }

    @Test
    fun `Spring gjør retry når det kastes en annen exception enn PensjongivendeInntektClientException`() {
        repeat(3) {
            SigrunMockDispatcher.enqueueResponse(
                withContentTypeApplicationJson { MockResponse(code = 200) },
            )
        }

        assertThrows<RuntimeException> {
            sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(fnr = FNR, SOKNAD_ID, 2024)
        }

        // @Retryable(maxRetries = 2), altså 1 initielt kall + 2 forsøk.
        SigrunMockDispatcher.antallKall() shouldBe 3
    }

    @Test
    fun `Finnes pensjonsgivende inntekt for år`() {
        with(SigrunMockDispatcher) {
            enqueueMockResponse(FNR, "2020")
        }

        val response = sykepengegrunnlagForNaeringsdrivende.finnesPensjonsgivendeInntektForAar(FNR, 2020)
        SigrunMockDispatcher.antallKall() shouldBe 1

        response shouldBe true
    }

    @Test
    fun `Finnes ikke pensjonsgivende inntekt for år`() {
        with(SigrunMockDispatcher) {
            enqueueResponse(sigrun404Feil())
        }

        val response = sykepengegrunnlagForNaeringsdrivende.finnesPensjonsgivendeInntektForAar(FNR, 2020)
        SigrunMockDispatcher.antallKall() shouldBe 1

        response shouldBe false
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
                            datoForFastsetting = LocalDate.parse("2020-07-17"),
                            skatteordning = Skatteordning.FASTLAND,
                            pensjonsgivendeInntektAvLoennsinntekt = 0,
                        ),
                        PensjonsgivendeInntekt(
                            datoForFastsetting = LocalDate.parse("2020-07-19"),
                            skatteordning = Skatteordning.SVALBARD,
                            pensjonsgivendeInntektAvNaeringsinntekt = 0,
                        ),
                    ),
            )
        }

        val response = sykepengegrunnlagForNaeringsdrivende.finnesPensjonsgivendeInntektForAar(FNR, 2020)
        SigrunMockDispatcher.antallKall() shouldBe 1

        response shouldBe false
    }
}
