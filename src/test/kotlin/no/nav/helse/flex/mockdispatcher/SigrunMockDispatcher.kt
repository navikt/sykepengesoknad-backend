package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.inntektskomponenten.HentPensjonsgivendeInntektResponse
import no.nav.helse.flex.client.inntektskomponenten.PensjonsgivendeInntekt
import no.nav.helse.flex.client.inntektskomponenten.Skatteordning
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object SigrunMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val fnr = request.headers["Nav-Personident"]!!
        val inntektsAar = request.headers["inntektsaar"]!!
        if (fnr == "87654321234") {
            when (inntektsAar) {
                "2024" -> {
                    return HentPensjonsgivendeInntektResponse(
                        norskPersonidentifikator = fnr,
                        inntektsaar = inntektsAar,
                        pensjonsgivendeInntekt =
                            listOf(
                                PensjonsgivendeInntekt(
                                    datoForFastsetting = "2024-07-17",
                                    skatteordning = Skatteordning.FASTLAND,
                                    pensjonsgivendeInntektAvLoennsinntekt = 100000,
                                    pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 5000,
                                    pensjonsgivendeInntektAvNaeringsinntekt = 850000,
                                    pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 2000,
                                ),
                            ),
                    ).tilMockResponse()
                }
                "2023" -> {
                    return HentPensjonsgivendeInntektResponse(
                        norskPersonidentifikator = fnr,
                        inntektsaar = inntektsAar,
                        pensjonsgivendeInntekt =
                            listOf(
                                PensjonsgivendeInntekt(
                                    datoForFastsetting = "2023-07-17",
                                    skatteordning = Skatteordning.FASTLAND,
                                    pensjonsgivendeInntektAvLoennsinntekt = 100000,
                                    pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 5000,
                                    pensjonsgivendeInntektAvNaeringsinntekt = 770000,
                                    pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 2000,
                                ),
                            ),
                    ).tilMockResponse()
                }
                "2022" -> {
                    return HentPensjonsgivendeInntektResponse(
                        norskPersonidentifikator = fnr,
                        inntektsaar = inntektsAar,
                        pensjonsgivendeInntekt =
                            listOf(
                                PensjonsgivendeInntekt(
                                    datoForFastsetting = "2022-07-17",
                                    skatteordning = Skatteordning.FASTLAND,
                                    pensjonsgivendeInntektAvLoennsinntekt = 100000,
                                    pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 5000,
                                    pensjonsgivendeInntektAvNaeringsinntekt = 700000,
                                    pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 2000,
                                ),
                            ),
                    ).tilMockResponse()
                }
                else -> return MockResponse().setResponseCode(404)
            }
        }
        when (inntektsAar) {
            "2024" -> {
                return HentPensjonsgivendeInntektResponse(
                    norskPersonidentifikator = fnr,
                    inntektsaar = inntektsAar,
                    pensjonsgivendeInntekt =
                        listOf(
                            PensjonsgivendeInntekt(
                                datoForFastsetting = "2024-07-17",
                                skatteordning = Skatteordning.FASTLAND,
                                pensjonsgivendeInntektAvLoennsinntekt = 100000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 5000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 400000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 2000,
                            ),
                        ),
                ).tilMockResponse()
            }
            "2023" -> {
                return HentPensjonsgivendeInntektResponse(
                    norskPersonidentifikator = fnr,
                    inntektsaar = inntektsAar,
                    pensjonsgivendeInntekt =
                        listOf(
                            PensjonsgivendeInntekt(
                                datoForFastsetting = "2023-07-17",
                                skatteordning = Skatteordning.FASTLAND,
                                pensjonsgivendeInntektAvLoennsinntekt = 100000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 5000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 350000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 2000,
                            ),
                        ),
                ).tilMockResponse()
            }
            "2022" -> {
                return HentPensjonsgivendeInntektResponse(
                    norskPersonidentifikator = fnr,
                    inntektsaar = inntektsAar,
                    pensjonsgivendeInntekt =
                        listOf(
                            PensjonsgivendeInntekt(
                                datoForFastsetting = "2022-07-17",
                                skatteordning = Skatteordning.FASTLAND,
                                pensjonsgivendeInntektAvLoennsinntekt = 100000,
                                pensjonsgivendeInntektAvLoennsinntektBarePensjonsdel = 5000,
                                pensjonsgivendeInntektAvNaeringsinntekt = 300000,
                                pensjonsgivendeInntektAvNaeringsinntektFraFiskeFangstEllerFamiliebarnehage = 2000,
                            ),
                        ),
                ).tilMockResponse()
            }
            else -> return MockResponse().setResponseCode(404)
        }
    }

    private fun HentPensjonsgivendeInntektResponse.tilMockResponse(): MockResponse {
        return MockResponse().setBody(this.serialisertTilString()).addHeader("Content-Type", "application/json")
    }
}
