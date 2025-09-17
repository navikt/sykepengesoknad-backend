package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object BrregMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                RollerDto(
                    roller =
                        listOf(
                            RolleDto(
                                rolletype = Rolletype.INNH,
                                organisasjonsnummer = "orgnummer",
                                organisasjonsnavn = "orgnavn",
                            ),
                        ),
                ).serialisertTilString(),
            )
}
