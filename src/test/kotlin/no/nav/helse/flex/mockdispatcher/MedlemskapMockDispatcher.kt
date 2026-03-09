package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.medlemskap.MedlemskapVurderingResponse
import no.nav.helse.flex.medlemskap.MedlemskapVurderingSvarType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object MedlemskapMockDispatcher : FellesQueueDispatcher<MedlemskapVurderingResponse>(
    defaultFactory = { _: RecordedRequest ->
        MedlemskapVurderingResponse(
            svar = MedlemskapVurderingSvarType.JA,
            sporsmal = emptyList(),
        )
    },
) {
    fun enqueueHttpFeil(statuskode: Int) {
        super.enqueueResponse(MockResponse().setResponseCode(statuskode))
    }
}
