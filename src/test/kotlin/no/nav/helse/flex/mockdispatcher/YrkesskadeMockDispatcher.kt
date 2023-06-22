package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.yrkesskade.HarYsSak
import no.nav.helse.flex.client.yrkesskade.HarYsSakerRequest
import no.nav.helse.flex.client.yrkesskade.HarYsSakerResponse
import no.nav.helse.flex.client.yrkesskade.SakerResponse
import no.nav.helse.flex.logger
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object YrkesskadeMockDispatcher : Dispatcher() {
    val log = logger()
    val queuedSakerRespons = mutableListOf<SakerResponse>()
    override fun dispatch(request: RecordedRequest): MockResponse {
        return when (request.requestLine) {
            "POST /api/v1/saker/har-yrkesskade-eller-yrkessykdom HTTP/1.1" -> {
                harSakerMock(request)
            }

            "POST /api/v1/saker/ HTTP/1.1" -> {
                sakerMock()
            }

            else -> {
                log.error("Ukjent api: " + request.requestLine)
                MockResponse().setResponseCode(404)
            }
        }
    }

    fun sakerMock(): MockResponse {
        if (queuedSakerRespons.isEmpty()) {
            return MockResponse()
                .setResponseCode(200)
                .setBody(SakerResponse(emptyList()).serialisertTilString())
                .addHeader("Content-Type", "application/json")
        }
        val poppedElement = queuedSakerRespons.removeAt(queuedSakerRespons.size - 1)

        return MockResponse()
            .setResponseCode(200)
            .setBody(poppedElement.serialisertTilString())
            .addHeader("Content-Type", "application/json")
    }

    fun harSakerMock(request: RecordedRequest): MockResponse {
        val ysReq: HarYsSakerRequest = OBJECT_MAPPER.readValue(request.body.readUtf8())

        if (ysReq.foedselsnumre.contains(FNR_MED_YRKESSKADE)) {
            return skapHarSakerResponse(listOf(HarYsSak.JA, HarYsSak.MAA_SJEKKES_MANUELT).random())
        }
        return skapHarSakerResponse(HarYsSak.NEI)
    }

    fun skapHarSakerResponse(harYsSak: HarYsSak): MockResponse {
        return MockResponse().setBody(
            HarYsSakerResponse(
                harYrkesskadeEllerYrkessykdom = harYsSak,
                beskrivelser = listOf("Yrkesskade"),
                kilde = "Infotrygd",
                kildeperiode = null

            ).serialisertTilString()
        )
            .addHeader("Content-Type", "application/json")
    }
}

const val FNR_MED_YRKESSKADE = "12154752342"
