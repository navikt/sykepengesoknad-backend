package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.Skatteordning
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object SigrunMockDispatcher : FellesQueueDispatcher<HentPensjonsgivendeInntektResponse>(
    defaultFactory = { it: RecordedRequest ->
        val fnr = it.getHeader("Nav-Personident") ?: "fnr"
        val inntektsaar = it.getHeader("inntektsaar") ?: "år"
        HentPensjonsgivendeInntektResponse(
            norskPersonidentifikator = fnr,
            inntektsaar = inntektsaar,
            pensjonsgivendeInntekt =
                listOf(
                    PensjonsgivendeInntekt(
                        datoForFastsetting = "ikke-relevant",
                        skatteordning = Skatteordning.FASTLAND,
                        pensjonsgivendeInntektAvNaeringsinntekt = 500_000,
                    ),
                ),
        )
    },
) {
    fun enqueueMockResponse(
        fnr: String,
        inntektsaar: String,
        skatteordning: Skatteordning = Skatteordning.FASTLAND,
        inntekt: List<PensjonsgivendeInntekt> =
            listOf(
                PensjonsgivendeInntekt(
                    datoForFastsetting = "$inntektsaar-07-17",
                    skatteordning = skatteordning,
                    pensjonsgivendeInntektAvNaeringsinntekt = 500_000,
                ),
            ),
    ) = super.enqueue(
        HentPensjonsgivendeInntektResponse(
            norskPersonidentifikator = fnr,
            inntektsaar = inntektsaar,
            pensjonsgivendeInntekt = inntekt,
        ),
    )

    fun sigrun404Feil() =
        MockResponse()
            .setResponseCode(404)
            .setBody("{\"errorCode\": \"PGIF-008\", \"errorMessage\": \"Ingen pensjonsgivende inntekt funnet.\"}")

    fun sigrun500Feil() =
        MockResponse()
            .setResponseCode(500)
            .setBody("{\"errorCode\": \"PGIF-006\", \"errorMessage\": \"Intern feil i Sigrun.\"}")
}
