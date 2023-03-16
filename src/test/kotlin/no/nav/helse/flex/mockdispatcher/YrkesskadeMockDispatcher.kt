package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.yrkesskade.HarYsSak
import no.nav.helse.flex.client.yrkesskade.HarYsSakerResponse
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object YrkesskadeMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        return skapResponse()
    }

    fun skapResponse(): MockResponse {
        return MockResponse().setBody(
            HarYsSakerResponse(
                harYrkesskadeEllerYrkessykdom = HarYsSak.NEI,
                beskrivelser = listOf("Yrkesskade"),
                kilde = "Infotrygd",
                kildeperiode = null

            ).serialisertTilString()
        )
            .addHeader("Content-Type", "application/json")
    }
}
