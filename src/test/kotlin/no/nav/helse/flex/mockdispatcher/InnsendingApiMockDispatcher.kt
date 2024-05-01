package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.innsendingapi.EttersendingRequest
import no.nav.helse.flex.client.innsendingapi.EttersendingResponse
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
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
                MockResponse().setResponseCode(200).setBody(
                    EttersendingResponse(
                        innsendingsId = UUID.randomUUID().toString(),
                    ).serialisertTilString(),
                )
            }
        } else if (request.requestLine.startsWith("DELETE /ekstern/v1/ettersending/")) {
            slettEttersendingRequests.add(request)
            withContentTypeApplicationJson {
                MockResponse().setResponseCode(200).setBody(
                    """{
                      "status": "null",
                      "info": "null"
                    }""",
                )
            }
        } else {
            log.error("Ukjent api: " + request.requestLine)
            MockResponse().setResponseCode(404)
        }
    }

    fun getOpprettEttersendingRequests(): List<RecordedRequest> = opprettEttersendRequests.toList()

    fun getSlettEttersendingRequests(): List<RecordedRequest> = slettEttersendingRequests.toList()

    fun getOpprettEttersendingLastRequest(): EttersendingRequest {
        return OBJECT_MAPPER.readValue(opprettEttersendRequests.last().body.readUtf8())
    }
}
