package no.nav.helse.flex.service

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.client.brreg.HentRollerRequest
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should throw`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource

@TestPropertySource(properties = ["BRREG_RETRY_ATTEMPTS=1"])
class SelvstendigNaringsdrivendeInfoServiceTest : FakesTestOppsett() {
    @Autowired
    lateinit var brregMockWebServer: MockWebServer

    @Autowired
    lateinit var selvstendigNaringsdrivendeInfoService: SelvstendigNaringsdrivendeInfoService

    @Test
    fun `Hent lagret SelvstendigNaringsdrivendeInfo`() {
        val rollerDto =
            RollerDto(
                roller =
                    listOf(
                        RolleDto(
                            rolletype = Rolletype.INNH,
                            organisasjonsnummer = "orgnummer",
                            organisasjonsnavn = "orgnavn",
                        ),
                    ),
            )

        brregMockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(rollerDto.serialisertTilString()),
        )

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(FolkeregisterIdenter("11111111111", andreIdenter = emptyList()))
            .roller
            .first()
            .also {
                it.orgnavn `should be equal to` "orgnavn"
                it.orgnummer `should be equal to` "orgnummer"
                it.rolletype `should be equal to` "INNH"
            }
    }

    @Test
    fun `Hent lagret SelvstendigNaringsdrivendeInfo med flere identer`() {
        val rollerDto =
            RollerDto(
                roller =
                    listOf(
                        RolleDto(
                            rolletype = Rolletype.INNH,
                            organisasjonsnummer = "orgnummer",
                            organisasjonsnavn = "orgnavn",
                        ),
                    ),
            )
        repeat(2) {
            brregMockWebServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(rollerDto.serialisertTilString()),
            )
        }

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(
                FolkeregisterIdenter(
                    "11111111111",
                    andreIdenter = listOf("22222222222"),
                ),
            ).roller.size `should be equal to` 2
    }

    @Test
    fun `Har riktig payload i request til Brreg`() {
        val rollerDto =
            RollerDto(
                roller =
                    listOf(
                        RolleDto(
                            rolletype = Rolletype.DAGL,
                            organisasjonsnummer = "orgnummer",
                            organisasjonsnavn = "orgnavn",
                        ),
                    ),
            )

        brregMockWebServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(rollerDto.serialisertTilString()),
        )

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(FolkeregisterIdenter("11111111111", andreIdenter = emptyList()))

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

        invoking {
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(FolkeregisterIdenter("11111111111", andreIdenter = emptyList()))
        } `should throw` Exception::class
    }
}
