package no.nav.helse.flex.service

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.client.brreg.HentRollerRequest
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.test.context.TestPropertySource
import org.springframework.web.client.HttpServerErrorException

@TestPropertySource(properties = ["BRREG_RETRY_ATTEMPTS=1", "VENTETID_RETRY_ATTEMPTS=1"])
class SelvstendigNaringsdrivendeInfoServiceTest : FakesTestOppsett() {
    @Autowired
    @Qualifier("brregMockWebServer")
    lateinit var brregMockWebServer: MockWebServer

    @Autowired
    @Qualifier("ventetidMockWebServer")
    lateinit var ventetidMockWebServer: MockWebServer

    @Autowired
    lateinit var selvstendigNaringsdrivendeInfoService: SelvstendigNaringsdrivendeInfoService

    @Test
    fun `SelvstendigNaringsdrivendeInfo returnerer roller`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagRollerDto(Rolletype.INNH).serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
                    FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                )

        selvstendigNaringsdrivendeInfo.roller.single().also {
            it.orgnavn `should be equal to` "orgnavn"
            it.orgnummer `should be equal to` "orgnummer"
            it.rolletype `should be equal to` "INNH"
        }
    }

    @Test
    fun `SelvstendigNaringsdrivendeInfo returnerer roller for flere identer`() {
        repeat(2) {
            brregMockWebServer.enqueue(
                withContentTypeApplicationJson {
                    MockResponse().setBody(lagRollerDto(Rolletype.INNH).serialisertTilString())
                },
            )
        }

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
                    FolkeregisterIdenter(
                        "11111111111",
                        andreIdenter = listOf("22222222222"),
                    ),
                )

        selvstendigNaringsdrivendeInfo.roller.size `should be equal to` 2
    }

    @Test
    fun `Har riktig payload i request`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagRollerDto(Rolletype.DAGL).serialisertTilString())
            },
        )

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
            )

        brregMockWebServer.takeRequest().body.readUtf8() `should be equal to`
            HentRollerRequest(
                fnr = "11111111111",
                rolleTyper =
                    listOf(
                        Rolletype.INNH,
                        Rolletype.DTPR,
                        Rolletype.DTSO,
                        Rolletype.KOMP,
                    ),
            ).serialisertTilString()
    }

    @Test
    fun `Kaster exception ved feil fra Brreg`() {
        brregMockWebServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Feil i api"),
        )

        assertThrows<RuntimeException> {
            selvstendigNaringsdrivendeInfoService.hentSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
            )
        }.also {
            it.message `should be equal to` "500 Server Error: \"Feil i api\""
        }
    }

    @Test
    fun `Kaster exception når det ikke blir funnet roller i Brreg`() {
        brregMockWebServer.enqueue(
            MockResponse()
                .setResponseCode(404)
                .setBody("Fant ingen roller"),
        )

        assertThrows<RuntimeException> {
            selvstendigNaringsdrivendeInfoService.hentSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
            )
        }.also {
            it.message `should be equal to` "404 Client Error: \"Fant ingen roller\""
        }
    }

    @Test
    fun `Kaster exception ved feil ved kall til flex-syketilfelle`() {
        ventetidMockWebServer.enqueue(MockResponse().setResponseCode(500))

        assertThrows<HttpServerErrorException> {
            selvstendigNaringsdrivendeInfoService.hentVentetid(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                sykmeldingId = "sykmeldingId",
            )
        }.also {
            it.message!!.startsWith("500 Server Error") `should be equal to` true
        }
    }

    private fun lagRollerDto(rolletype: Rolletype) =
        RollerDto(
            roller =
                listOf(
                    RolleDto(
                        rolletype = rolletype,
                        organisasjonsnummer = "orgnummer",
                        organisasjonsnavn = "orgnavn",
                    ),
                ),
        )
}
