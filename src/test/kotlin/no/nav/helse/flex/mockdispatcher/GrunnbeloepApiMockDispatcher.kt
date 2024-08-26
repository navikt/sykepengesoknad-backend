package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest

object GrunnbeloepApiMockDispatcher : QueueDispatcher() {
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

            "/historikk?fra=2019-01-01" -> {
                val responses =
                    listOf(
                        GrunnbeloepResponse(
                            dato = "2019-05-01",
                            grunnbeloep = 99858,
                            grunnbeloepPerMaaned = 8322,
                            gjennomsnittPerAar = 98866,
                            omregningsfaktor = 1.030707f,
                            virkningstidspunktForMinsteinntekt = "2019-05-27",
                        ),
                        GrunnbeloepResponse(
                            dato = "2020-05-01",
                            grunnbeloep = 101351,
                            grunnbeloepPerMaaned = 8446,
                            gjennomsnittPerAar = 100853,
                            omregningsfaktor = 1.014951f,
                            virkningstidspunktForMinsteinntekt = "2020-09-21",
                        ),
                        GrunnbeloepResponse(
                            dato = "2021-05-01",
                            grunnbeloep = 106399,
                            grunnbeloepPerMaaned = 8867,
                            gjennomsnittPerAar = 104716,
                            omregningsfaktor = 1.049807f,
                            virkningstidspunktForMinsteinntekt = "2021-05-24",
                        ),
                        GrunnbeloepResponse(
                            dato = "2022-05-01",
                            grunnbeloep = 111477,
                            grunnbeloepPerMaaned = 9290,
                            gjennomsnittPerAar = 109784,
                            omregningsfaktor = 1.047726f,
                            virkningstidspunktForMinsteinntekt = "2022-05-23",
                        ),
                        GrunnbeloepResponse(
                            dato = "2023-05-01",
                            grunnbeloep = 118620,
                            grunnbeloepPerMaaned = 9885,
                            gjennomsnittPerAar = 116239,
                            omregningsfaktor = 1.064076f,
                            virkningstidspunktForMinsteinntekt = "2023-05-26",
                        ),
                        GrunnbeloepResponse(
                            dato = "2024-05-01",
                            grunnbeloep = 124028,
                            grunnbeloepPerMaaned = 10336,
                            gjennomsnittPerAar = 122225,
                            omregningsfaktor = 1.045591f,
                            virkningstidspunktForMinsteinntekt = "2024-06-03",
                        ),
                    )

                MockResponse()
                    .setResponseCode(200)
                    .setBody(responses.serialisertTilString())
                    .addHeader("Content-Type", "application/json")
            }

            else -> MockResponse().setResponseCode(404)
        }
    }
}
