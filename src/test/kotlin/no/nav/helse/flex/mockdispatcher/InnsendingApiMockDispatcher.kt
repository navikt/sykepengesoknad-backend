package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.innsendingapi.EttersendingResponse
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.http.MediaType
import java.util.UUID

object InnsendingApiMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
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
}
