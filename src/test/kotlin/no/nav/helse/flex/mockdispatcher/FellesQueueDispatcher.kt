package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType

abstract class FellesQueueDispatcher<T : Any>(
    private val defaultFactory: (RecordedRequest) -> T,
) : QueueDispatcher() {
    fun clearQueue() {
        responseQueue.clear()
    }

    fun harRequestsIgjen(): Boolean = responseQueue.isNotEmpty()

    fun enqueue(objekt: T) {
        super.enqueueResponse(
            withContentTypeApplicationJson {
                MockResponse().setBody(objekt.serialisertTilString())
            },
        )
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (!harRequestsIgjen()) {
            enqueue(defaultFactory(request))
        }
        return super.dispatch(request)
    }
}

fun withContentTypeApplicationJson(createMockResponse: () -> MockResponse): MockResponse =
    createMockResponse().addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
