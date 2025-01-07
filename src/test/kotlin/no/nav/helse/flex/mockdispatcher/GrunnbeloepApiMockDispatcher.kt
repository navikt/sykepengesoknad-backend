package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.util.objectMapper
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate

object GrunnbeloepApiMockDispatcher : QueueDispatcher() {
    val grunnbelopHistorikk =
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
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
                """.trimIndent(),
        )

    override fun dispatch(request: RecordedRequest): MockResponse {
        // URL må dekodes siden det er "ø" i grunnbeløp.
        val path = URLDecoder.decode(request.path, StandardCharsets.UTF_8.name())
        val dato = LocalDate.parse(path.split("=").last())

        // g.api.nav.no har ikke data fra før 1967.
        if (dato.year < 1967) {
            return MockResponse().setResponseCode(500)
        }

        val response =
            when {
                path.startsWith("/grunnbeløp") -> dispatchGrunnbeloep(dato)
                path.startsWith("/historikk") -> dispatchGrunnbeloepHistorikk(dato)
                else -> return MockResponse().setResponseCode(500).setBody("Ukjent path: $path")
            }

        return MockResponse()
            .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .setResponseCode(200)
            .setBody(response!!)
    }

    private fun dispatchGrunnbeloep(dato: LocalDate): String? {
        val year = if (erFoerForsteMai(dato)) dato.year - 1 else dato.year
        return grunnbelopHistorikk[year] ?: grunnbelopHistorikk[year - 1]
    }

    private fun dispatchGrunnbeloepHistorikk(dato: LocalDate): String {
        val year = if (erFoerForsteMai(dato)) dato.year - 1 else dato.year
        return grunnbelopHistorikk.filterKeys { it >= year }.values.joinToString(prefix = "[", postfix = "]")
    }

    private fun erFoerForsteMai(date: LocalDate): Boolean {
        return date.isBefore(LocalDate.of(date.year, 5, 1))
    }
}

class GrunnbeloepApiMockDispatcherTest {
    private val mockWebServer: MockWebServer =
        MockWebServer().apply {
            dispatcher = GrunnbeloepApiMockDispatcher
        }

    private val restClient = RestClient.create()

    @Test
    fun `Returnerer grunnbeløp for 2021 når spør om 2022 før 2022-05-01`() {
        val grunnbeloepResponse = callDispatch("/grunnbeløp?dato=2022-01-01").body!!.toGrunnbeloepResponse()
        grunnbeloepResponse.dato `should be equal to` "2021-05-01"
    }

    @Test
    fun `Returnerer grunnbeløp for 2022 når spør om 2022 etter 2022-05-01`() {
        val grunnbeloepResponse = callDispatch("/grunnbeløp?dato=2022-06-01").body!!.toGrunnbeloepResponse()
        grunnbeloepResponse.dato `should be equal to` "2022-05-01"
    }

    @Test
    fun `Returnerer siste år som finnes når spør om et år som ikke finnes før 05-01 det aktulle året`() {
        val grunnbeloepResponse = callDispatch("/grunnbeløp?dato=2025-01-01").body!!.toGrunnbeloepResponse()
        grunnbeloepResponse.dato `should be equal to` "2024-05-01"
    }

    @Test
    fun `Returnerer siste år som finnes når spør om et år som ikke finnes etter 05-01 det aktuelle året`() {
        val grunnbeloepResponse = callDispatch("/grunnbeløp?dato=2025-06-01").body!!.toGrunnbeloepResponse()
        grunnbeloepResponse.dato `should be equal to` "2024-05-01"
    }

    @Test
    fun `Første år i historikk er 2018 når spør om 2019 før 2022-05-01`() {
        val response = callDispatch("/historikk/grunnbeløp?fra=2019-01-01").body!!.toGrunnbeloepResponseListe()
        response.size `should be equal to` 7
        response.first().dato `should be equal to` "2018-05-01"
        response.last().dato `should be equal to` "2024-05-01"
    }

    @Test
    fun `Første år i historikk er 2018 når spør om 2019 etter 2022-05-01`() {
        val response = callDispatch("/historikk/grunnbeløp?fra=2019-06-01").body!!.toGrunnbeloepResponseListe()
        response.size `should be equal to` 6
        response.first().dato `should be equal to` "2019-05-01"
        response.last().dato `should be equal to` "2024-05-01"
    }

    @Test
    fun `Returnerer siste år i historikk når spør om et år som ikke finnes før 05-01 det aktulle året`() {
        val response = callDispatch("/historikk/grunnbeløp?fra=2025-01-01").body!!.toGrunnbeloepResponseListe()
        response.size `should be equal to` 1
        response.last().dato `should be equal to` "2024-05-01"
    }

    @Test
    fun `Returnerer siste år i historikk når spør om et år som ikke finnes etter 05-01 det aktuelle året`() {
        val response = callDispatch("/historikk/grunnbeløp?fra=2025-01-01").body!!.toGrunnbeloepResponseListe()
        response.size `should be equal to` 1
        response.last().dato `should be equal to` "2024-05-01"
    }

    @Test
    fun `Får 500 feil når spør om grunnbeløp fra et for tidlig år`() {
        restClient.get()
            .uri(mockWebServer.url("/grunnbeløp?dato=1960-01-01").toUri())
            .runCatching {
                retrieve().toEntity(String::class.java)
            }.onFailure {
                it.message?.shouldStartWith("500 Server Error")
            }
    }

    @Test
    fun `Får 500 feil ved feil i URL`() {
        restClient.get()
            .uri(mockWebServer.url("/feil?dato=2024-01-01").toUri())
            .runCatching {
                retrieve().toEntity(String::class.java)
            }.onFailure {
                it.message `should be equal to` "500 Server Error: \"Ukjent path: /feil?dato=2024-01-01\""
            }
    }

    private fun callDispatch(path: String): ResponseEntity<String?> {
        val response =
            restClient.get()
                .uri(mockWebServer.url(path).toUri())
                .retrieve()
                .toEntity(String::class.java)
        return response
    }

    fun String.toGrunnbeloepResponse(): GrunnbeloepResponse {
        return objectMapper.readValue(this)
    }

    fun String.toGrunnbeloepResponseListe(): List<GrunnbeloepResponse> {
        return objectMapper.readValue(this)
    }
}
