package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest

object GrunnbeloepApoMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.path) {
            "/grunnbeloep?dato=2022-01-01" -> {
                val response =
                    GrunnbeloepResponse(
                        dato = "2022-01-01",
                        grunnbeloep = 106399,
                        grunnbeloepPerMaaned = 8866,
                        gjennomsnittPerAar = 104716,
                        omregningsfaktor = 1.028f,
                        virkningstidspunktForMinsteinntekt = "2022-05-01",
                    )
                MockResponse()
                    .setResponseCode(200)
                    .setBody(response.serialisertTilString())
            }

            "/historikk?fra=2020-01-01" -> {
                val responses =
                    listOf(
                        GrunnbeloepResponse(
                            dato = "2020-01-01",
                            grunnbeloep = 99858,
                            grunnbeloepPerMaaned = 8322,
                            gjennomsnittPerAar = 98312,
                            omregningsfaktor = 1.034f,
                            virkningstidspunktForMinsteinntekt = "2020-05-01",
                        ),
                        GrunnbeloepResponse(
                            dato = "2021-01-01",
                            grunnbeloep = 101351,
                            grunnbeloepPerMaaned = 8445,
                            gjennomsnittPerAar = 99648,
                            omregningsfaktor = 1.020f,
                            virkningstidspunktForMinsteinntekt = "2021-05-01",
                        ),
                    )
                MockResponse()
                    .setResponseCode(200)
                    .setBody(responses.serialisertTilString())
            }

            else -> MockResponse().setResponseCode(404)
        }
    }
}
