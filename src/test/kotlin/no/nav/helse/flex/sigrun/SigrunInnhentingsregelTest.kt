package no.nav.helse.flex.sigrun

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.mockdispatcher.SigrunMockDispatcher
import no.nav.helse.flex.mockdispatcher.lag404MockResponse
import no.nav.helse.flex.mockdispatcher.lagSigrunMockResponse
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val FNR = "12345678910"

class SigrunInnhentingsregelTest : FellesTestOppsett() {
    @BeforeEach
    fun resetMockWebServer() {
        SigrunMockDispatcher.antallKall.set(0)
    }

    @Test
    fun `Person har ferdiglignet inntekt 3 siste år`() {
        with(sigrunMockWebServer) {
            enqueue(lagSigrunMockResponse(FNR, "2023"))
            enqueue(lagSigrunMockResponse(FNR, "2022"))
            enqueue(lagSigrunMockResponse(FNR, "2021"))
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
    fun `Returnerer null når 2 første år ikke ikke finnes`() {
        with(sigrunMockWebServer) {
            repeat(3) {
                enqueue(lag404MockResponse())
            }
            enqueue(lagSigrunMockResponse(FNR, "2020"))
        }

        sykepengegrunnlagForNaeringsdrivende.hentRelevantPensjonsgivendeInntekt(FNR, 2024) `should be` null
        SigrunMockDispatcher.antallKall.get() `should be equal to` 4
    }
}
