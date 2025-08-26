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
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*

@TestPropertySource(properties = ["BRREG_RETRY_ATTEMPTS=1"])
class SelvstendigNaringsdrivendeInfoServiceTest : FakesTestOppsett() {
    @Autowired
    lateinit var brregServer: MockWebServer

    @Autowired
    lateinit var selvstendigNaringsdrivendeInfoService: SelvstendigNaringsdrivendeInfoService

    @Autowired
    lateinit var enhetsregisterMockServer: MockRestServiceServer

    private fun mockEnhetsregisterErDagmamma(
        orgnr: String,
        erDagmamma: Boolean,
    ) {
        val json = """{\"naeringskode1\": {\"kode\": \"${if (erDagmamma) "88.912" else "41.109"}\"}}"""
        enhetsregisterMockServer
            .expect(requestTo(containsString("/api/enheter/$orgnr")))
            .andRespond(withSuccess(json, org.springframework.http.MediaType.APPLICATION_JSON))
    }

    @Test
    fun `burde hente selvstendig næringsdrivende`() {
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

        brregServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(rollerDto.serialisertTilString()),
        )

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(FolkeregisterIdenter("fnr", andreIdenter = emptyList()))
            .roller
            .first()
            .also {
                it.orgnavn `should be equal to` "orgnavn"
                it.orgnummer `should be equal to` "orgnummer"
                it.rolletype `should be equal to` "INNH"
            }
    }

    @Test
    fun `burde hente selvstendig næringsdrivende for flere identer`() {
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
            brregServer.enqueue(
                MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(rollerDto.serialisertTilString()),
            )
        }

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(FolkeregisterIdenter("fnr", andreIdenter = listOf("fnr2")))
            .roller.size `should be equal to` 2
    }

    @Test
    fun `burde ha riktig payload i request`() {
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

        brregServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(rollerDto.serialisertTilString()),
        )

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(FolkeregisterIdenter("fnr", andreIdenter = emptyList()))

        brregServer.takeRequest().body.readUtf8() `should be equal to`
            HentRollerRequest(
                fnr = "fnr",
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
    fun `burde kaste feil fra brreg api`() {
        brregServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Feil i api"),
        )

        invoking {
            selvstendigNaringsdrivendeInfoService
                .hentSelvstendigNaringsdrivendeInfo(FolkeregisterIdenter("fnr", andreIdenter = emptyList()))
        } `should throw` Exception::class
    }
}
