package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.ereg.Navn
import no.nav.helse.flex.client.ereg.Nokkelinfo
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object EregMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val orgnummr = request.path!!.split("/")[3]
        val orgnavn =
            when (orgnummr) {
                "999333666" -> "BENSINSTASJONEN AS"
                "999333667" -> "FRILANSERANSETTER AS"
                "999888777" -> "KIOSKEN,AVD OSLO AS"
                else -> "UKJENT"
            }
        return skapResponse(orgnavn)
    }

    fun skapResponse(orgnavn: String): MockResponse =
        MockResponse()
            .setBody(Nokkelinfo(Navn(orgnavn)).serialisertTilString())
            .addHeader("Content-Type", "application/json")
}
