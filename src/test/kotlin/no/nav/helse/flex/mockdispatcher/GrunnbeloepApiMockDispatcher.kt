package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object GrunnbeloepApiMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val decodePathÆØÅ = URLDecoder.decode(request.path, StandardCharsets.UTF_8.name())
        return when (decodePathÆØÅ) {
            "/grunnbeløp?dato=2022-01-01" -> {
                val response =
                    GrunnbeloepResponse(
                        dato = "2022-01-01",
                        grunnbeløp = 106399,
                        grunnbeløpPerMaaned = 8866,
                        gjennomsnittPerÅr = 104716,
                        omregningsfaktor = 1.028f,
                        virkningstidspunktForMinsteinntekt = "2022-05-01",
                    )
                MockResponse()
                    .setResponseCode(200)
                    .setBody(response.serialisertTilString())
            }

            "/historikk/grunnbeløp?fra=2013-01-01" -> {
                val responses =
                    listOf(
                        GrunnbeloepResponse(
                            dato = "2013-05-01",
                            grunnbeløp = 85245,
                            grunnbeløpPerMaaned = 7104,
                            gjennomsnittPerÅr = 84204,
                            omregningsfaktor = 1.038029f,
                            virkningstidspunktForMinsteinntekt = "2013-06-24",
                        ),
                        GrunnbeloepResponse(
                            dato = "2014-05-01",
                            grunnbeløp = 88370,
                            grunnbeløpPerMaaned = 7364,
                            gjennomsnittPerÅr = 87328,
                            omregningsfaktor = 1.036659f,
                            virkningstidspunktForMinsteinntekt = "2014-06-30",
                        ),
                        GrunnbeloepResponse(
                            dato = "2015-05-01",
                            grunnbeløp = 90068,
                            grunnbeløpPerMaaned = 7506,
                            gjennomsnittPerÅr = 89502,
                            omregningsfaktor = 1.019214f,
                            virkningstidspunktForMinsteinntekt = "2015-06-01",
                        ),
                        GrunnbeloepResponse(
                            dato = "2016-05-01",
                            grunnbeløp = 92576,
                            grunnbeløpPerMaaned = 7715,
                            gjennomsnittPerÅr = 91740,
                            omregningsfaktor = 1.027846f,
                            virkningstidspunktForMinsteinntekt = "2016-05-30",
                        ),
                        GrunnbeloepResponse(
                            dato = "2017-05-01",
                            grunnbeløp = 93634,
                            grunnbeløpPerMaaned = 7803,
                            gjennomsnittPerÅr = 93281,
                            omregningsfaktor = 1.011428f,
                            virkningstidspunktForMinsteinntekt = "2017-05-29",
                        ),
                        GrunnbeloepResponse(
                            dato = "2018-05-01",
                            grunnbeløp = 96883,
                            grunnbeløpPerMaaned = 8074,
                            gjennomsnittPerÅr = 95800,
                            omregningsfaktor = 1.034699f,
                            virkningstidspunktForMinsteinntekt = "2018-06-04",
                        ),
                        GrunnbeloepResponse(
                            dato = "2019-05-01",
                            grunnbeløp = 99858,
                            grunnbeløpPerMaaned = 8322,
                            gjennomsnittPerÅr = 98866,
                            omregningsfaktor = 1.030707f,
                            virkningstidspunktForMinsteinntekt = "2019-05-27",
                        ),
                        GrunnbeloepResponse(
                            dato = "2020-05-01",
                            grunnbeløp = 101351,
                            grunnbeløpPerMaaned = 8446,
                            gjennomsnittPerÅr = 100853,
                            omregningsfaktor = 1.014951f,
                            virkningstidspunktForMinsteinntekt = "2020-09-21",
                        ),
                        GrunnbeloepResponse(
                            dato = "2021-05-01",
                            grunnbeløp = 106399,
                            grunnbeløpPerMaaned = 8867,
                            gjennomsnittPerÅr = 104716,
                            omregningsfaktor = 1.049807f,
                            virkningstidspunktForMinsteinntekt = "2021-05-24",
                        ),
                        GrunnbeloepResponse(
                            dato = "2022-05-01",
                            grunnbeløp = 111477,
                            grunnbeløpPerMaaned = 9290,
                            gjennomsnittPerÅr = 109784,
                            omregningsfaktor = 1.047726f,
                            virkningstidspunktForMinsteinntekt = "2022-05-23",
                        ),
                        GrunnbeloepResponse(
                            dato = "2023-05-01",
                            grunnbeløp = 118620,
                            grunnbeløpPerMaaned = 9885,
                            gjennomsnittPerÅr = 116239,
                            omregningsfaktor = 1.064076f,
                            virkningstidspunktForMinsteinntekt = "2023-05-26",
                        ),
                        GrunnbeloepResponse(
                            dato = "2024-05-01",
                            grunnbeløp = 124028,
                            grunnbeløpPerMaaned = 10336,
                            gjennomsnittPerÅr = 122225,
                            omregningsfaktor = 1.045591f,
                            virkningstidspunktForMinsteinntekt = "2024-06-03",
                        ),
                    )

                MockResponse()
                    .setResponseCode(200)
                    .setBody(responses.serialisertTilString())
                    .addHeader("Content-Type", "application/json")
            }

            "/historikk/grunnbeløp?fra=2019-01-01" -> {
                val responses =
                    listOf(
                        GrunnbeloepResponse(
                            dato = "2019-05-01",
                            grunnbeløp = 99858,
                            grunnbeløpPerMaaned = 8322,
                            gjennomsnittPerÅr = 98866,
                            omregningsfaktor = 1.030707f,
                            virkningstidspunktForMinsteinntekt = "2019-05-27",
                        ),
                        GrunnbeloepResponse(
                            dato = "2020-05-01",
                            grunnbeløp = 101351,
                            grunnbeløpPerMaaned = 8446,
                            gjennomsnittPerÅr = 100853,
                            omregningsfaktor = 1.014951f,
                            virkningstidspunktForMinsteinntekt = "2020-09-21",
                        ),
                        GrunnbeloepResponse(
                            dato = "2021-05-01",
                            grunnbeløp = 106399,
                            grunnbeløpPerMaaned = 8867,
                            gjennomsnittPerÅr = 104716,
                            omregningsfaktor = 1.049807f,
                            virkningstidspunktForMinsteinntekt = "2021-05-24",
                        ),
                        GrunnbeloepResponse(
                            dato = "2022-05-01",
                            grunnbeløp = 111477,
                            grunnbeløpPerMaaned = 9290,
                            gjennomsnittPerÅr = 109784,
                            omregningsfaktor = 1.047726f,
                            virkningstidspunktForMinsteinntekt = "2022-05-23",
                        ),
                        GrunnbeloepResponse(
                            dato = "2023-05-01",
                            grunnbeløp = 118620,
                            grunnbeløpPerMaaned = 9885,
                            gjennomsnittPerÅr = 116239,
                            omregningsfaktor = 1.064076f,
                            virkningstidspunktForMinsteinntekt = "2023-05-26",
                        ),
                        GrunnbeloepResponse(
                            dato = "2024-05-01",
                            grunnbeløp = 124028,
                            grunnbeløpPerMaaned = 10336,
                            gjennomsnittPerÅr = 122225,
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
