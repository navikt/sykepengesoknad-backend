package no.nav.helse.flex

import no.nav.helse.flex.client.syfotilgangskontroll.SyfoTilgangskontrollClient.Companion.NAV_PERSONIDENT_HEADER
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.SimpleSykmelding
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.util.OBJECT_MAPPER
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.manyTimes
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest
import java.time.LocalDate

fun BaseTestClass.mockSyfoTilgangskontroll(
    tilgang: Boolean,
    fnr: String
) {
    syfotilgangskontrollMockRestServiceServer!!
        .expect(requestTo("http://syfotilgang/syfo-tilgangskontroll/api/tilgang/navident/person"))
        .andExpect(header(NAV_PERSONIDENT_HEADER, fnr))
        .andRespond(
            if (tilgang) {
                withSuccess(
                    OBJECT_MAPPER.writeValueAsBytes(
                        "Har tilgang"
                    ),
                    MediaType.APPLICATION_JSON
                )
            } else {
                withUnauthorizedRequest()
            }
        )
}

fun BaseTestClass.mockFlexSyketilfelleSykeforloep(sykmeldingId: String, oppfolgingsdato: LocalDate = LocalDate.now()) {
    return mockFlexSyketilfelleSykeforloep(
        listOf(
            Sykeforloep(
                oppfolgingsdato = oppfolgingsdato,
                sykmeldinger = listOf(SimpleSykmelding(id = sykmeldingId, fom = oppfolgingsdato, tom = oppfolgingsdato))
            )
        )
    )
}

fun BaseTestClass.mockFlexSyketilfelleSykeforloep(sykeforloep: List<Sykeforloep>) {
    flexSyketilfelleMockRestServiceServer!!
        .expect(manyTimes(),requestTo("http://flex-syketilfelle/api/v1/sykeforloep?hentAndreIdenter=false"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                OBJECT_MAPPER.writeValueAsBytes(
                    sykeforloep
                ),
                MediaType.APPLICATION_JSON
            )
        )
}

fun BaseTestClass.mockFlexSyketilfelleErUtaforVentetid(sykmeldingId: String, utafor: Boolean) {
    flexSyketilfelleMockRestServiceServer!!
        .expect(requestTo("http://flex-syketilfelle/api/v1/ventetid/$sykmeldingId/erUtenforVentetid?hentAndreIdenter=false"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                OBJECT_MAPPER.writeValueAsBytes(
                    utafor
                ),
                MediaType.APPLICATION_JSON
            )
        )
}

fun BaseTestClass.mockFlexSyketilfelleArbeidsgiverperiode(
    andreKorrigerteRessurser: String? = null,
    arbeidsgiverperiode: Arbeidsgiverperiode? = null
) {
    fun url(): String {
        val baseUrl = "http://flex-syketilfelle/api/v1/arbeidsgiverperiode?hentAndreIdenter=false"
        andreKorrigerteRessurser?.let {
            return baseUrl + "&andreKorrigerteRessurser=$andreKorrigerteRessurser"
        }
        return baseUrl
    }
    if (arbeidsgiverperiode != null) {
        flexSyketilfelleMockRestServiceServer!!
            .expect(requestTo(url()))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    OBJECT_MAPPER.writeValueAsBytes(
                        arbeidsgiverperiode
                    ),
                    MediaType.APPLICATION_JSON
                )
            )
    } else {

        flexSyketilfelleMockRestServiceServer!!
            .expect(requestTo(url()))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withNoContent())
    }
}
