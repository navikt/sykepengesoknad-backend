package no.nav.helse.flex.service

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.client.brreg.Rolle
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired

class RolleutskriftServiceTest : FakesTestOppsett() {
    @Autowired
    lateinit var brregServer: MockWebServer

    @Autowired
    lateinit var rolleutskriftService: RolleutskriftService

    @Test
    fun `burde hente selvstendig næringsdrivende`() {
        val forventetRolleListe =
            listOf(
                Rolle(
                    rolletype = Rolletype.INNH,
                    organisasjonsnummer = "orgnummer",
                    organisasjonsnavn = "orgnavn",
                ),
            )

        brregServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(forventetRolleListe.serialisertTilString()),
        )

        rolleutskriftService
            .hentSelvstendigNaringsdrivende("fnr")
            .organisasjoner
            .first()
            .also {
                it.orgnavn `should be equal to` "orgnavn"
                it.orgnummer `should be equal to` "orgnummer"
                it.rolletype `should be equal to` "ENK"
            }
    }

    @Test
    fun `burde hente selvstendig næringsdrivende roller`() {
        val forventetRolleListe =
            listOf(
                Rolle(
                    rolletype = Rolletype.INNH,
                    organisasjonsnummer = "orgnummer",
                    organisasjonsnavn = "orgnavn",
                ),
            )

        brregServer.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(forventetRolleListe.serialisertTilString()),
        )

        rolleutskriftService
            .hentSelvstendigNaringsdrivendeRoller("fnr")
            .first()
            .also {
                it.rolletype `should be equal to` Rolletype.INNH
                it.organisasjonsnummer `should be equal to` "orgnummer"
                it.organisasjonsnavn `should be equal to` "orgnavn"
            }
    }

    @Test
    fun `burde kaste feil fra brreg api`() {
        brregServer.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("Feil i api"),
        )

        assertThrows<Exception> {
            rolleutskriftService
                .hentSelvstendigNaringsdrivendeRoller("fnr")
                .isEmpty() `should be equal to` true
        }
    }
}
