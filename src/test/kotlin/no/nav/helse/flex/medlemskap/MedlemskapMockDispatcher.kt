package no.nav.helse.flex.medlemskap

import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.http.MediaType

object MedlemskapMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        when (request.headers["fnr"]) {
            "31111111111" -> return MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
                    )
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

            "31111111112" -> return MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal = emptyList()
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

            "31111111113" -> return MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.NEI,
                    sporsmal = emptyList()
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

            "31111111114" -> return MockResponse().setResponseCode(500)
            "31111111115" -> return MockResponse().setResponseCode(400)
            "31111111116" -> return MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = emptyList()
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

            "31111111117" -> return MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE
                    )
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

            "31111111118" -> return MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.NEI,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE
                    )
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)

            else -> return MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.JA,
                    sporsmal = emptyList()
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        }
    }
}
