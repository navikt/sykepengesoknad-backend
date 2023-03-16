package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.yrkesskade.HarYsSak
import no.nav.helse.flex.client.yrkesskade.HarYsSakerRequest
import no.nav.helse.flex.client.yrkesskade.HarYsSakerResponse
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object YrkesskadeMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val ysReq: HarYsSakerRequest = OBJECT_MAPPER.readValue(request.body.readUtf8())

        if (ysReq.foedselsnumre.contains(FNR_MED_YRKESSKADE)) {
            return skapResponse(listOf(HarYsSak.JA, HarYsSak.MAA_SJEKKES_MANUELT).random())
        }
        return skapResponse(HarYsSak.NEI)
    }

    fun skapResponse(harYsSak: HarYsSak): MockResponse {
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
