package no.nav.helse.flex.mockdispatcher

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.client.yrkesskade.SakerResponse
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.serialisertTilString

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
                MockResponse(code = 404)
            }
        }

    fun sakerMock(): MockResponse {
        if (queuedSakerRespons.isEmpty()) {
            return withContentTypeApplicationJson {
                MockResponse(code = 200, body = SakerResponse(emptyList()).serialisertTilString())
            }
        }
        val poppedElement = queuedSakerRespons.removeAt(queuedSakerRespons.size - 1)

        return withContentTypeApplicationJson {
            MockResponse(code = 200, body = poppedElement.serialisertTilString())
        }
    }
}

const val FNR_MED_YRKESSKADE = "12154752342"
