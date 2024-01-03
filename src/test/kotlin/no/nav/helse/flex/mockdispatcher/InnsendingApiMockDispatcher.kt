package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.innsendingapi.EttersendingRequest
import no.nav.helse.flex.client.innsendingapi.EttersendingResponse
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.http.MediaType
import java.util.UUID

object InnsendingApiMockDispatcher : QueueDispatcher() {
    private val requests = mutableListOf<RecordedRequest>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        requests.add(request)

        fun withContentTypeApplicationJson(createMockResponse: () -> MockResponse): MockResponse =
            createMockResponse().addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

        if (responseQueue.peek() != null) {
            return withContentTypeApplicationJson { responseQueue.take() }
        }

        return withContentTypeApplicationJson {
            MockResponse().setResponseCode(200).setBody(
                EttersendingResponse(
                    innsendingsId = UUID.randomUUID().toString(),
                ).serialisertTilString(),
            )
        }
    }

    fun getRequests(): List<RecordedRequest> = requests.toList()

    fun lastRequest(): EttersendingRequest {
        return OBJECT_MAPPER.readValue(requests.last().body.readUtf8())
    }
}
