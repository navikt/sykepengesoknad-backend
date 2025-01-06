package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.atomic.AtomicInteger

object SigrunMockDispatcher : QueueDispatcher() {
    val antallKall = AtomicInteger(0)

    override fun dispatch(request: RecordedRequest): MockResponse {
        antallKall.incrementAndGet()

        if (responseQueue.peek() != null) {
            return withContentTypeApplicationJson { responseQueue.take() }
        }

        // Sigrun API svarer med 404 hvis det ikke finnes pensjonsgivende inntekt for det aktuelle året.
        return withContentTypeApplicationJson {
            lag404MockResponse()
        }
    }

    fun MockWebServer.enqueueMockResponse(
        fnr: String,
        inntektsaar: String,
        inntekt: List<PensjonsgivendeInntekt>,
    ) = enqueue(lagMockResponse(fnr, inntektsaar, inntekt))

    fun lagMockResponse(
        fnr: String,
        inntektsaar: String,
        skatteordning: Skatteordning = Skatteordning.FASTLAND,
    ) = lagMockResponse(
        fnr,
        inntektsaar,
        listOf(
            PensjonsgivendeInntekt(
                datoForFastsetting = "$inntektsaar-07-17",
                skatteordning = skatteordning,
                pensjonsgivendeInntektAvNaeringsinntekt = 500_000,
            ),
        ),
    )

    fun lagMockResponse(
        fnr: String,
        inntektsaar: String,
        inntekter: List<PensjonsgivendeInntekt>,
    ) = MockResponse().setResponseCode(200).setBody(
        HentPensjonsgivendeInntektResponse(
            norskPersonidentifikator = fnr,
            inntektsaar = inntektsaar,
            pensjonsgivendeInntekt = inntekter,
        ).serialisertTilString(),
    )

    fun lag404MockResponse(): MockResponse =
        MockResponse()
            .setResponseCode(404)
            .setBody("{\"errorCode\": \"PGIF-008\", \"errorMessage\": \"Ingen pensjonsgivende inntekt funnet.\"}")

    fun lag500MockResponse(): MockResponse =
        MockResponse()
            .setResponseCode(500)
            .setBody("{\"errorCode\": \"PGIF-006\", \"errorMessage\": \"Oppgitt inntektsår er ikke støttet.\"}")
}
