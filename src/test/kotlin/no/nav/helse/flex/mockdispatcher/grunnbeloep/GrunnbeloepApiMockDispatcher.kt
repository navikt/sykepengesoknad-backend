package no.nav.helse.flex.mockdispatcher.grunnbeloep

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.mockdispatcher.FellesQueueDispatcher
import no.nav.helse.flex.util.objectMapper
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

object GrunnbeloepApiMockDispatcher : FellesQueueDispatcher<List<GrunnbeloepResponse>>(
    defaultFactory = { request ->
        // URL må dekodes siden det er "ø" i grunnbeløp.
        val path = URLDecoder.decode(request.path, StandardCharsets.UTF_8.name())
        val dato = LocalDate.parse(path.split("=").last())

        lagGrunnbeløpHistorikkResponse(dato)
    },
)

fun erFoerForsteMai(date: LocalDate): Boolean = date.isBefore(LocalDate.of(date.year, 5, 1))

fun lagGrunnbeløpHistorikkResponse(dato: LocalDate): List<GrunnbeloepResponse> {
    val year = if (erFoerForsteMai(dato)) dato.year - 1 else dato.year
    return grunnbelopHistorikk
        .filterKeys { it >= year }
        .values
        .toList()
}

val grunnbelopHistorikk: Map<Int, GrunnbeloepResponse> =
    mapOf(
        2012 to
            """
            {
              "dato": "2012-05-01",
              "grunnbeløp": 82122,
              "grunnbeløpPerMåned": 6844,
              "gjennomsnittPerÅr": 81153,
              "omregningsfaktor": 1.036685
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2013 to
            """
            {
              "dato": "2013-05-01",
              "grunnbeløp": 85245,
              "grunnbeløpPerMåned": 7104,
              "gjennomsnittPerÅr": 84204,
              "omregningsfaktor": 1.038029,
              "virkningstidspunktForMinsteinntekt": "2013-06-24"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2014 to
            """
            {
              "dato": "2014-05-01",
              "grunnbeløp": 88370,
              "grunnbeløpPerMåned": 7364,
              "gjennomsnittPerÅr": 87328,
              "omregningsfaktor": 1.036659,
              "virkningstidspunktForMinsteinntekt": "2014-06-30"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2015 to
            """
            {
              "dato": "2015-05-01",
              "grunnbeløp": 90068,
              "grunnbeløpPerMaaned": 7506,
              "gjennomsnittPerÅr": 89502,
              "omregningsfaktor": 1.019214,
              "virkningstidspunktForMinsteinntekt": "2015-06-01"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2016 to
            """
            {
              "dato": "2016-05-01",
              "grunnbeløp": 92576,
              "grunnbeløpPerMaaned": 7715,
              "gjennomsnittPerÅr": 91740,
              "omregningsfaktor": 1.027846,
              "virkningstidspunktForMinsteinntekt": "2016-05-30"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2017 to
            """
            {
              "dato": "2017-05-01",
              "grunnbeløp": 93634,
              "grunnbeløpPerMaaned": 7803,
              "gjennomsnittPerÅr": 93281,
              "omregningsfaktor": 1.011428,
              "virkningstidspunktForMinsteinntekt": "2017-05-29"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2018 to
            """
            {
              "dato": "2018-05-01",
              "grunnbeløp": 96883,
              "grunnbeløpPerMaaned": 8074,
              "gjennomsnittPerÅr": 95800,
              "omregningsfaktor": 1.034699,
              "virkningstidspunktForMinsteinntekt": "2018-06-04"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2019 to
            """
            {
              "dato": "2019-05-01",
              "grunnbeløp": 99858,
              "grunnbeløpPerMaaned": 8322,
              "gjennomsnittPerÅr": 98866,
              "omregningsfaktor": 1.030707,
              "virkningstidspunktForMinsteinntekt": "2019-05-27"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2020 to
            """
            {
              "dato": "2020-05-01",
              "grunnbeløp": 101351,
              "grunnbeløpPerMaaned": 8446,
              "gjennomsnittPerÅr": 100853,
              "omregningsfaktor": 1.014951,
              "virkningstidspunktForMinsteinntekt": "2020-09-21"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2021 to
            """
            {
              "dato":"2021-05-01",
              "grunnbeløp":106399,
              "grunnbeløpPerMaaned":8867,
              "gjennomsnittPerÅr":104716,
              "omregningsfaktor":1.049807,
              "virkningstidspunktForMinsteinntekt":"2021-05-24"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2022 to
            """
            {
              "dato": "2022-05-01",
              "grunnbeløp": 111477,
              "grunnbeløpPerMaaned": 9290,
              "gjennomsnittPerÅr": 109784,
              "omregningsfaktor": 1.047726,
              "virkningstidspunktForMinsteinntekt": "2022-05-23"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2023 to
            """
            {
              "dato": "2023-05-01",
              "grunnbeløp": 118620,
              "grunnbeløpPerMaaned": 9885,
              "gjennomsnittPerÅr": 116239,
              "omregningsfaktor": 1.064076,
              "virkningstidspunktForMinsteinntekt": "2023-05-26"
            }
            """.trimIndent().toGrunnbeloepResponse(),
        2024 to
            """
            {
              "dato": "2024-05-01",
              "grunnbeløp": 124028,
              "grunnbeløpPerMaaned": 10336,
              "gjennomsnittPerÅr": 122225,
              "omregningsfaktor": 1.045591,
              "virkningstidspunktForMinsteinntekt": "2024-06-03"
            }
            """.trimIndent().toGrunnbeloepResponse(),
    )

fun String.toGrunnbeloepResponse(): GrunnbeloepResponse = objectMapper.readValue(this)

fun String.toGrunnbeloepResponseListe(): List<GrunnbeloepResponse> = objectMapper.readValue(this)
