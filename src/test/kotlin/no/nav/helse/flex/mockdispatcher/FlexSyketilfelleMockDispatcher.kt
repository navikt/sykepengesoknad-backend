package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.domain.SimpleSykmelding
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.sykmelding.SykmeldingRequest
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.*

object FlexSyketilfelleMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        return if (request.requestLine == "POST /api/v1/sykeforloep?hentAndreIdenter=false HTTP/1.1") {
            val request: SykmeldingRequest = OBJECT_MAPPER.readValue(request.body.readUtf8())

            val sykeforloep =
                Sykeforloep(
                    oppfolgingsdato = request.sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.first().fom,
                    sykmeldinger =
                        listOf(
                            SimpleSykmelding(
                                id = request.sykmeldingKafkaMessage.sykmelding.id,
                                fom = request.sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.first().fom,
                                tom = request.sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder.first().tom,
                            ),
                        ),
                )

            withContentTypeApplicationJson {
                MockResponse().setResponseCode(200).setBody(
                    listOf(
                        sykeforloep,
                    ).serialisertTilString(),
                )
            }
        } else {
            InnsendingApiMockDispatcher.log.error("Ukjent api: " + request.requestLine)
            MockResponse().setResponseCode(404)
        }
    }
}
