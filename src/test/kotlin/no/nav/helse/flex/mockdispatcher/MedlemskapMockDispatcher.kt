package no.nav.helse.flex.mockdispatcher

import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.medlemskap.MedlemskapVurderingResponse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSvarType

object MedlemskapMockDispatcher : FellesQueueDispatcher<MedlemskapVurderingResponse>(
    defaultFactory = { _: RecordedRequest ->
        MedlemskapVurderingResponse(
            svar = MedlemskapVurderingSvarType.JA,
            sporsmal = emptyList(),
        )
    },
) {
    fun enqueueHttpFeil(statuskode: Int) {
        super.enqueueResponse(MockResponse(code = statuskode))
    }
}
