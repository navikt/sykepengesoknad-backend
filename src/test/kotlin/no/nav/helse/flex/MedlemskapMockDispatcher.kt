package no.nav.helse.flex

import no.nav.helse.flex.client.medlemskap.MedlemskapVurderingResponse
import no.nav.helse.flex.client.medlemskap.MedlemskapVurderingSporsmal
import no.nav.helse.flex.client.medlemskap.MedlemskapVurderingSvarType
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object MedlemskapMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        return MockResponse().setBody(
            MedlemskapVurderingResponse(
                svar = MedlemskapVurderingSvarType.UAVKLART,
                sporsmal = listOf(
                    MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                    MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                    MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                    MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
                )
            ).serialisertTilString()
        ).addHeader("Content-Type", "application/json")
    }
}
