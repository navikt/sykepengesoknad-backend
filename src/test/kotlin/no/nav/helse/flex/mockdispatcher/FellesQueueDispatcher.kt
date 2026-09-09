package no.nav.helse.flex.mockdispatcher

import mockwebserver3.MockResponse
import mockwebserver3.QueueDispatcher
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.util.serialisertTilString
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

    /** Legger et rått svar i køen, i tillegg til enqueue(T) som serialiserer et domeneobjekt. */
    fun enqueueResponse(response: MockResponse) {
        enqueue(response)
    }

    /** responseQueue er protected i QueueDispatcher, så tester utenfor klassen trenger denne. */
    fun harRequestsIgjen(): Boolean = responseQueue.isNotEmpty()

    fun enqueue(objekt: T) {
        enqueue(
            withContentTypeApplicationJson {
                MockResponse(body = objekt.serialisertTilString())
            },
        )
    }

    override fun dispatch(request: RecordedRequest): MockResponse {
        antallKall.incrementAndGet()
        if (responseQueue.isEmpty()) {
            enqueue(defaultFactory(request))
        }
        return responseQueue.take()
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
    createMockResponse()
        .newBuilder()
        .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build()
