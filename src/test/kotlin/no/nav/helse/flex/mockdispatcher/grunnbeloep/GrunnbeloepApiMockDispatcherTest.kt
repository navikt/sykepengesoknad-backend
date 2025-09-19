package no.nav.helse.flex.mockdispatcher.grunnbeloep

import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldStartWith
import org.junit.jupiter.api.Test
import org.springframework.http.ResponseEntity
import org.springframework.web.client.RestClient

class GrunnbeloepApiMockDispatcherTest {
    private val mockWebServer: MockWebServer =
        MockWebServer().apply {
            dispatcher = GrunnbeloepApiMockDispatcher
        }

    private val restClient = RestClient.create()

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
        restClient
            .get()
            .uri(mockWebServer.url("/grunnbeløp?dato=1960-01-01").toUri())
            .runCatching {
                retrieve().toEntity(String::class.java)
            }.onFailure {
                it.message?.shouldStartWith("500 Server Error")
            }
    }

    @Test
    fun `Får 500 feil ved feil i URL`() {
        restClient
            .get()
            .uri(mockWebServer.url("/feil?dato=2024-01-01").toUri())
            .runCatching {
                retrieve().toEntity(String::class.java)
            }.onFailure {
                it.message `should be equal to` "500 Server Error: \"Ukjent path: /feil?dato=2024-01-01\""
            }
    }

    private fun callDispatch(path: String): ResponseEntity<String?> {
        val response =
            restClient
                .get()
                .uri(mockWebServer.url(path).toUri())
                .retrieve()
                .toEntity(String::class.java)
        return response
    }
}
