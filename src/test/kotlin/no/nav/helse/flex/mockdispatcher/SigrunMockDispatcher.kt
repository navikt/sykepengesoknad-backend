package no.nav.helse.flex.mockdispatcher

import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.client.sigrun.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.sigrun.PensjonsgivendeInntekt
import no.nav.helse.flex.client.sigrun.SigrunRequest
import no.nav.helse.flex.client.sigrun.Skatteordning
import no.nav.helse.flex.util.objectMapper
import tools.jackson.module.kotlin.readValue

object SigrunMockDispatcher : FellesQueueDispatcher<HentPensjonsgivendeInntektResponse>(
    defaultFactory = { it: RecordedRequest ->
        val sigrunRequest: SigrunRequest = objectMapper.readValue(it.body!!.utf8())
        val fnr = sigrunRequest.personident
        val inntektsaar = sigrunRequest.inntektsaar
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
        MockResponse(code = 404, body = "{\"errorCode\": \"PGIF-008\", \"errorMessage\": \"Ingen pensjonsgivende inntekt funnet.\"}")

    fun sigrun500Feil() = MockResponse(code = 500, body = "{\"errorCode\": \"PGIF-006\", \"errorMessage\": \"Intern feil i Sigrun.\"}")
}
