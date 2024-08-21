package no.nav.helse.flex.mockdispatcher

import no.nav.helse.flex.client.aareg.*
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.time.LocalDate

object AaregMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val response =
            listOf(
                Arbeidsforhold(
                    arbeidsgiver = Arbeidsgiver("vanlig", "1234"),
                    ansettelsesperiode = Ansettelsesperiode(Periode(LocalDate.now(), LocalDate.now())),
                    opplysningspliktig = Opplysningspliktig("vanlig", "1234"),
                ),
            )
        return MockResponse()
            .setResponseCode(200)
            .setBody(response.serialisertTilString())
            .addHeader("Content-Type", "application/json")
    }
}
