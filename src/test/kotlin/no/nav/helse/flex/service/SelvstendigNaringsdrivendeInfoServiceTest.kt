package no.nav.helse.flex.service

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.invoking
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should throw`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class SelvstendigNaringsdrivendeInfoServiceTest : FakesTestOppsett() {
    @Autowired
    lateinit var brregServer: MockWebServer

    @Autowired
    lateinit var selvstendigNaringsdrivendeInfoService: SelvstendigNaringsdrivendeInfoService

    @Test
    fun `burde hente selvstendig næringsdrivende`() {
        val rolleListe =
            listOf(
                RolleDto(
                    rolletype = Rolletype.INNH,
                    organisasjonsnummer = "orgnummer",
                    organisasjonsnavn = "orgnavn",
                ),
            )

        brregServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(rolleListe.serialisertTilString()),
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
    fun `burde hente kun selvstendig næringsdrivende roller`() {
        val rolleListe =
            listOf(
                RolleDto(
                    rolletype = Rolletype.DAGL,
                    organisasjonsnummer = "orgnummer",
                    organisasjonsnavn = "orgnavn",
                ),
            )

        brregServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(rolleListe.serialisertTilString()),
        )

        selvstendigNaringsdrivendeInfoService
            .hentSelvstendigNaringsdrivendeInfo(FolkeregisterIdenter("fnr", andreIdenter = emptyList()))
            .roller
            .size `should be equal to` 0
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
