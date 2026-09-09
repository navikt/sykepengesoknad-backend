package no.nav.helse.flex.mockdispatcher

import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import no.nav.helse.flex.client.flexsyketilfelle.SykmeldingRequest
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.SimpleSykmelding
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

object FlexSyketilfelleMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse =
        when (request.requestLine) {
            "POST /api/v1/sykeforloep?hentAndreIdenter=false HTTP/1.1" -> {
                val requestBody: SykmeldingRequest = objectMapper.readValue(request.body!!.utf8())
                val sykeforloep =
                    Sykeforloep(
                        oppfolgingsdato =
                            requestBody.sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder
                                .first()
                                .fom,
                        sykmeldinger =
                            listOf(
                                SimpleSykmelding(
                                    id = requestBody.sykmeldingKafkaMessage.sykmelding.id,
                                    fom =
                                        requestBody.sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder
                                            .first()
                                            .fom,
                                    tom =
                                        requestBody.sykmeldingKafkaMessage.sykmelding.sykmeldingsperioder
                                            .first()
                                            .tom,
                                ),
                            ),
                    )

                withContentTypeApplicationJson {
                    MockResponse(code = 200, body = listOf(sykeforloep).serialisertTilString())
                }
            }
            "POST /api/v2/arbeidsgiverperiode?hentAndreIdenter=false HTTP/1.1" -> {
                withContentTypeApplicationJson {
                    MockResponse(
                        code = 200,
                        body =
                            Arbeidsgiverperiode(
                                oppbruktArbeidsgiverperiode = true,
                                antallBrukteDager = 16,
                                arbeidsgiverPeriode = Periode(fom = LocalDate.now().minusDays(17), tom = LocalDate.now().minusDays(1)),
                            ).serialisertTilString(),
                    )
                }
            }
            else -> {
                InnsendingApiMockDispatcher.log.error("Ukjent api: " + request.requestLine)
                MockResponse(code = 404)
            }
        }
}
