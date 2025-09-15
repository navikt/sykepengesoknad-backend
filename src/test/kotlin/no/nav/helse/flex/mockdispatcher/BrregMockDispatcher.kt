package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.brreg.RolleDto
import no.nav.helse.flex.client.brreg.RollerDto
import no.nav.helse.flex.client.brreg.Rolletype
import no.nav.helse.flex.mockdispatcher.AaregMockDispatcher.queuedArbeidsforholdOversikt
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object BrregMockDispatcher : Dispatcher() {
    val queuedRolleOversikt = mutableListOf<List<RollerDto>>()

    fun clear() {
        queuedRolleOversikt.clear()
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (queuedRolleOversikt.isEmpty()) {
            return MockResponse()
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

        val poppedElement = queuedArbeidsforholdOversikt.removeAt(YrkesskadeMockDispatcher.queuedSakerRespons.size)

        return MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(poppedElement.serialisertTilString())
    }
}
