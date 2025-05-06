package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.yrkesskade.SakerResponse
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object YrkesskadeMockDispatcher : Dispatcher() {
    val log = logger()
    val queuedSakerRespons = mutableListOf<SakerResponse>()

    override fun dispatch(request: RecordedRequest): MockResponse =
        when (request.requestLine) {
            "POST /api/v1/saker/ HTTP/1.1" -> {
                sakerMock()
            }

            else -> {
                log.error("Ukjent api: " + request.requestLine)
                MockResponse().setResponseCode(404)
            }
        }

    fun sakerMock(): MockResponse {
        if (queuedSakerRespons.isEmpty()) {
            return MockResponse()
                .setResponseCode(200)
                .setBody(SakerResponse(emptyList()).serialisertTilString())
                .addHeader("Content-Type", "application/json")
        }
        val poppedElement = queuedSakerRespons.removeAt(queuedSakerRespons.size - 1)

        return MockResponse()
            .setResponseCode(200)
            .setBody(poppedElement.serialisertTilString())
            .addHeader("Content-Type", "application/json")
    }
}

const val FNR_MED_YRKESSKADE = "12154752342"
