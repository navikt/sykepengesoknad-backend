package no.nav.helse.flex.mockdispatcher

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest

object VentetidMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        if (responseQueue.peek() != null) {
            return withContentTypeApplicationJson { responseQueue.take() }
        }

        // Default response når tester ikke har lagt til andre responser i responseQueue.
        return withContentTypeApplicationJson {
            MockResponse().setResponseCode(404)
        }
    }
}
