package no.nav.helse.flex.mockdispatcher

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.client.ereg.Navn
import no.nav.helse.flex.client.ereg.Nokkelinfo
import no.nav.helse.flex.util.serialisertTilString

object EregMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val orgnummr = request.url.encodedPath.split("/")[3]
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
        withContentTypeApplicationJson {
            MockResponse(body = Nokkelinfo(Navn(orgnavn)).serialisertTilString())
        }
}
