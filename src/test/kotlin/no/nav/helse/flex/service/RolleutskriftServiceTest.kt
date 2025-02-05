package no.nav.helse.flex.service

import no.nav.helse.flex.FakesTestOppsett
import no.nav.helse.flex.client.brreg.Rolle
import no.nav.helse.flex.client.brreg.RolleType
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.amshove.kluent.`should be equal to`
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

class RolleutskriftServiceTest : FakesTestOppsett() {
    @Autowired
    lateinit var brregServer: MockWebServer

    @Autowired
    lateinit var rolleutskriftService: RolleutskriftService

    @Test
    fun `burde hente selvstendig næringsdrivende roller`() {
        val forventetRolleListe =
            listOf(
                Rolle(
                    rolleType = RolleType.INNH,
                    organisasjonsnummer = "orgnummer",
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
                it.rolleType `should be equal to` RolleType.INNH
                it.organisasjonsnummer `should be equal to` "orgnummer"
            }
    }
}
