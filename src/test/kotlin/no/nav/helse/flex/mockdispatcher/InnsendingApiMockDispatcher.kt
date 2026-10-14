package no.nav.helse.flex.mockdispatcher

import mockwebserver3.MockResponse
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.client.innsendingapi.EttersendingRequest
import no.nav.helse.flex.client.innsendingapi.EttersendingResponse
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import tools.jackson.module.kotlin.readValue
import java.util.UUID

object InnsendingApiMockDispatcher : QueueDispatcher() {
    private val opprettEttersendRequests = mutableListOf<RecordedRequest>()
    private val slettEttersendingRequests = mutableListOf<RecordedRequest>()
    val log = logger()

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (responseQueue.peek() != null) {
            return withContentTypeApplicationJson { responseQueue.take() }
        }

        return if (request.requestLine == "POST /ekstern/v1/ettersending HTTP/1.1") {
            opprettEttersendRequests.add(request)

            withContentTypeApplicationJson {
                MockResponse(code = 200, body = EttersendingResponse(innsendingsId = UUID.randomUUID().toString()).serialisertTilString())
            }
        } else if (request.requestLine.startsWith("DELETE /ekstern/v1/ettersending/")) {
            slettEttersendingRequests.add(request)
            withContentTypeApplicationJson {
                MockResponse(code = 200, body = """{ "status": "null", "info": "null" }""")
            }
        } else {
            log.error("Ukjent api: " + request.requestLine)
            MockResponse(code = 404)
        }
    }

    fun getOpprettEttersendingRequests(): List<RecordedRequest> = opprettEttersendRequests.toList()

    fun getSlettEttersendingRequests(): List<RecordedRequest> = slettEttersendingRequests.toList()

    fun getOpprettEttersendingLastRequest(): EttersendingRequest = objectMapper.readValue(opprettEttersendRequests.last().body!!.utf8())
}
