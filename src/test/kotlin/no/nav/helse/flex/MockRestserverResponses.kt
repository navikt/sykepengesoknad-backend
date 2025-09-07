package no.nav.helse.flex

import no.nav.helse.flex.client.istilgangskontroll.IstilgangskontrollClient.Companion.NAV_PERSONIDENT_HEADER
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.SimpleSykmelding
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.domain.VentetidResponse
import no.nav.helse.flex.util.objectMapper
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

fun FellesTestOppsett.mockIstilgangskontroll(
    tilgang: Boolean,
    fnr: String,
) {
    istilgangskontrollMockRestServiceServer
        .expect(requestTo("http://istilgang/api/tilgang/navident/person"))
        .andExpect(header(NAV_PERSONIDENT_HEADER, fnr))
        .andRespond(
            if (tilgang) {
                withSuccess(
                    objectMapper.writeValueAsBytes(
                        "Har tilgang",
                    ),
                    MediaType.APPLICATION_JSON,
                )
            } else {
                withUnauthorizedRequest()
            },
        )
}

fun FellesTestOppsett.mockFlexSyketilfelleSykeforloep(
    sykmeldingId: String,
    oppfolgingsdato: LocalDate = LocalDate.now(),
) = mockFlexSyketilfelleSykeforloep(
    listOf(
        Sykeforloep(
            oppfolgingsdato = oppfolgingsdato,
            sykmeldinger = listOf(SimpleSykmelding(id = sykmeldingId, fom = oppfolgingsdato, tom = oppfolgingsdato)),
        ),
    ),
)

fun FellesTestOppsett.mockFlexSyketilfelleSykeforloep(sykeforloep: List<Sykeforloep>) {
    flexSyketilfelleMockRestServiceServer
        .expect(manyTimes(), requestTo("http://flex-syketilfelle/api/v1/sykeforloep?hentAndreIdenter=false"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                objectMapper.writeValueAsBytes(
                    sykeforloep,
                ),
                MediaType.APPLICATION_JSON,
            ),
        )
}

fun FellesTestOppsett.mockFlexSyketilfelleErUtenforVentetid(
    sykmeldingId: String,
    erUtenforVentetid: Boolean,
) {
    flexSyketilfelleMockRestServiceServer
        .expect(requestTo("http://flex-syketilfelle/api/v1/ventetid/$sykmeldingId/erUtenforVentetid?hentAndreIdenter=false"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                objectMapper.writeValueAsBytes(
                    erUtenforVentetid,
                ),
                MediaType.APPLICATION_JSON,
            ),
        )
}

fun FellesTestOppsett.mockFlexSyketilfelleVentetid(
    sykmeldingId: String,
    ventetidResponse: VentetidResponse,
) {
    flexSyketilfelleMockRestServiceServer
        .expect(requestTo("http://flex-syketilfelle/api/v1/ventetid/$sykmeldingId/ventetid?hentAndreIdenter=false"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                objectMapper.writeValueAsBytes(
                    ventetidResponse,
                ),
                MediaType.APPLICATION_JSON,
            ),
        )
}

fun FellesTestOppsett.mockFlexSyketilfelleArbeidsgiverperiode(
    andreKorrigerteRessurser: String? = null,
    arbeidsgiverperiode: Arbeidsgiverperiode? = null,
) {
    fun url(): String {
        val baseUrl = "http://flex-syketilfelle/api/v2/arbeidsgiverperiode?hentAndreIdenter=false"
        andreKorrigerteRessurser?.let {
            return baseUrl + "&andreKorrigerteRessurser=$andreKorrigerteRessurser"
        }
        return baseUrl
    }
    if (arbeidsgiverperiode != null) {
        flexSyketilfelleMockRestServiceServer
            .expect(requestTo(url()))
            .andExpect(method(HttpMethod.POST))
            .andRespond(
                withSuccess(
                    objectMapper.writeValueAsBytes(
                        arbeidsgiverperiode,
                    ),
                    MediaType.APPLICATION_JSON,
                ),
            )
    } else {
        flexSyketilfelleMockRestServiceServer
            .expect(requestTo(url()))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withNoContent())
    }
}
