package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.mockdispatcher.withContentTypeApplicationJson
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest

object MedlemskapMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        if (responseQueue.peek() != null) {
            return withContentTypeApplicationJson { responseQueue.take() }
        }

        // Svarer med status JA med mindre testene har lagt til andre svar i responseQueueu. Det gjør at tester som
        // ikke eksplisitt angir et svar hverken vil få medlemskapspørsmål eller spørsmålet om ARBEID_UTENFOR_NORGE.
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
