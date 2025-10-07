package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import java.util.concurrent.atomic.AtomicInteger

abstract class FellesQueueDispatcher<T : Any>(
    private val defaultFactory: (RecordedRequest) -> T,
) : QueueDispatcher() {
    val antallKall = AtomicInteger(0)

    fun antallKall() = antallKall.get()

    init {
        registrer(this)
    }

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
        antallKall.incrementAndGet()
        if (!harRequestsIgjen()) {
            enqueue(defaultFactory(request))
        }
        return super.dispatch(request)
    }

    override fun setFailFast(failFastResponse: MockResponse?) {
        antallKall.incrementAndGet()
        super.setFailFast(failFastResponse)
    }

    companion object {
        private val registrerte = mutableSetOf<FellesQueueDispatcher<*>>()

        internal fun registrer(dispatcher: FellesQueueDispatcher<*>) {
            registrerte.add(dispatcher)
        }

        fun alle(): List<FellesQueueDispatcher<*>> = registrerte.toList()
    }
}

fun withContentTypeApplicationJson(createMockResponse: () -> MockResponse): MockResponse =
    createMockResponse().addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
