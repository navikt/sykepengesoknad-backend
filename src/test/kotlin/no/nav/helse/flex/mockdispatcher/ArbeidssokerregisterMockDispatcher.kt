package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeRequest
import no.nav.helse.flex.client.arbeidssokerregister.ArbeidssokerperiodeResponse
import no.nav.helse.flex.client.arbeidssokerregister.BrukerResponse
import no.nav.helse.flex.client.arbeidssokerregister.MetadataResponse
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.time.LocalDateTime
import java.util.*

object ArbeidssokerregisterMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        if (responseQueue.peek() != null) {
            return withContentTypeApplicationJson { responseQueue.take() }
        }

        // TODO: for å sjekke at dispatcher ikke blir kallt. Kan endres til å default ikke svar.
        val request: ArbeidssokerperiodeRequest = objectMapper.readValue(request.body.readUtf8())
        if (request.identitetsnummer == "22222222222") {
            return MockResponse().setResponseCode(404)
        }

        return MockResponse()
            .setResponseCode(200)
            .setBody(listOf(skapArbeidssokerperiodeResponse()).serialisertTilString())
    }
}

fun skapArbeidssokerperiodeResponse(
    avsluttet: Boolean = false,
    periodeId: String = UUID.randomUUID().toString(),
): ArbeidssokerperiodeResponse =
    ArbeidssokerperiodeResponse(
        startet =
            MetadataResponse(
                tidspunkt = LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC),
                utfoertAv =
                    BrukerResponse(
                        type = "VEILEDER",
                        id = "Z999999",
                    ),
                kilde = "VEILEDER",
                aarsak = "NYE_ARBEIDSSOKERPERIODER",
                tidspunktFraKilde = null,
            ),
        avsluttet =
            if (avsluttet) {
                MetadataResponse(
                    tidspunkt = LocalDateTime.now().toInstant(java.time.ZoneOffset.UTC),
                    utfoertAv =
                        BrukerResponse(
                            type = "VEILEDER",
                            id = "Z999999",
                        ),
                    kilde = "VEILEDER",
                    aarsak = "NYE_ARBEIDSSOKERPERIODER",
                    tidspunktFraKilde = null,
                )
            } else {
                null
            },
        periodeId = periodeId,
    )
