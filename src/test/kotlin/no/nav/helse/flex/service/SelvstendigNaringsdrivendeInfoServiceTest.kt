package no.nav.helse.flex.service

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.client.brreg.HentRollerRequest
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.domain.Venteperiode
import no.nav.helse.flex.domain.VenteperiodeRequest
import no.nav.helse.flex.domain.VenteperiodeResponse
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
import java.time.LocalDate

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

    val venteperiodeResponse = VenteperiodeResponse(Venteperiode(LocalDate.now(), LocalDate.now().plusDays(1)))

    @Test
    fun `SelvstendigNaringsdrivendeInfo returnerer roller og venteperiode`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagRollerDto(Rolletype.INNH).serialisertTilString())
            },
        )

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(venteperiodeResponse.serialisertTilString())
            },
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
                    FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                    "test-sykmelding-id",
                )

        selvstendigNaringsdrivendeInfo.roller.single().also {
            it.orgnavn `should be equal to` "orgnavn"
            it.orgnummer `should be equal to` "orgnummer"
            it.rolletype `should be equal to` "INNH"
        }

        selvstendigNaringsdrivendeInfo.ventetid.also {
            it!!.fom `should be equal to` LocalDate.now()
            it.tom `should be equal to` LocalDate.now().plusDays(1)
        }
    }

    @Test
    fun `SelvstendigNaringsdrivendeInfo returnerer roller og venteperiode for flere identer`() {
        repeat(2) {
            brregMockWebServer.enqueue(
                withContentTypeApplicationJson {
                    MockResponse().setBody(lagRollerDto(Rolletype.INNH).serialisertTilString())
                },
            )
        }

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(venteperiodeResponse.serialisertTilString())
            },
        )

        ventetidMockWebServer.enqueue(
            MockResponse().setBody(
                VenteperiodeResponse(
                    Venteperiode(
                        LocalDate.now(),
                        LocalDate.now().plusDays(1),
                    ),
                ).serialisertTilString(),
            ),
        )

        val selvstendigNaringsdrivendeInfo =
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(
                    FolkeregisterIdenter(
                        "11111111111",
                        andreIdenter = listOf("22222222222"),
                    ),
                    "test-sykmelding-id",
                )

        selvstendigNaringsdrivendeInfo.roller.size `should be equal to` 2

        selvstendigNaringsdrivendeInfo.ventetid.also {
            it!!.fom `should be equal to` LocalDate.now()
            it.tom `should be equal to` LocalDate.now().plusDays(1)
        }
    }

    @Test
    fun `Har riktig payload i request`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagRollerDto(Rolletype.DAGL).serialisertTilString())
            },
        )

        ventetidMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(venteperiodeResponse.serialisertTilString())
            },
        )

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                "test-sykmelding-id",
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

        ventetidMockWebServer.takeRequest().body.readUtf8() `should be equal to`
            VenteperiodeRequest(harForsikring = true).serialisertTilString()
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
                "test-sykmelding-id",
            )
        }.also {
            it.message `should be equal to` "500 Server Error: \"Feil i api\""
        }
    }

    @Test
    fun `Kaster exception ved feil ved kall til flex-syketilfelle`() {
        brregMockWebServer.enqueue(
            withContentTypeApplicationJson {
                MockResponse().setBody(lagRollerDto(Rolletype.DAGL).serialisertTilString())
            },
        )

        ventetidMockWebServer.enqueue(MockResponse().setResponseCode(500))

        assertThrows<HttpServerErrorException> {
            selvstendigNaringsdrivendeInfoService.hentSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter("11111111111", andreIdenter = emptyList()),
                "test-sykmelding-id",
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
