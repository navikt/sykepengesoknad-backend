package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.aareg.*
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest

object AaregMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val response = ArbeidsforholdoversiktResponse(arbeidsforholdoversikter = emptyList())
        return MockResponse()
            .setResponseCode(200)
            .setBody(response.serialisertTilString())
            .addHeader("Content-Type", "application/json")
    }
}
