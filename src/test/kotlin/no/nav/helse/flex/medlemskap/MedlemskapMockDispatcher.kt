package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.util.concurrent.atomic.AtomicInteger

object MedlemskapMockDispatcher : QueueDispatcher() {
    val antallKall = AtomicInteger(0)

    override fun dispatch(request: RecordedRequest): MockResponse {
        antallKall.incrementAndGet()
        if (responseQueue.peek() != null) {
            return withContentTypeApplicationJson { responseQueue.take() }
        }

        // Default response når tester ikke har lag til andre responser i responseQueue.
        // Svarer med vurdering av medlemskap: JA. Det gjør at tester som ikke eksplisitt angir et svar hverken vil
        // få medlemskapspørsmål eller spørsmålet om ARBEID_UTENFOR_NORGE.
        return withContentTypeApplicationJson {
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal = emptyList(),
                ).serialisertTilString(),
            )
        }
    }
}
