package no.nav.helse.flex

import no.nav.helse.flex.client.flexsyketilfelle.SammeVentetidPeriode
import no.nav.helse.flex.client.flexsyketilfelle.SammeVentetidResponse
import no.nav.helse.flex.client.istilgangskontroll.IstilgangskontrollClient.Companion.NAV_PERSONIDENT_HEADER
import no.nav.helse.flex.domain.Arbeidsgiverperiode
import no.nav.helse.flex.domain.Periode
import no.nav.helse.flex.domain.SimpleSykmelding
import no.nav.helse.flex.domain.Sykeforloep
import no.nav.helse.flex.util.objectMapper
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.web.client.ExpectedCount.manyTimes
import org.springframework.test.web.client.match.MockRestRequestMatchers.*
import org.springframework.test.web.client.response.MockRestResponseCreators.*
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

fun FellesTestOppsett.mockFlexSyketilfelleSykeforloep(
    sykmeldingIder: Set<String>,
    oppfolgingsdato: LocalDate = LocalDate.now(),
) = mockFlexSyketilfelleSykeforloep(
    listOf(
        Sykeforloep(
            oppfolgingsdato = oppfolgingsdato,
            sykmeldinger = sykmeldingIder.map { SimpleSykmelding(id = it, fom = oppfolgingsdato, tom = oppfolgingsdato) },
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

fun FellesTestOppsett.mockFlexSyketilfelleHentSykmeldingerMedSammeVentetid(sykmeldingIder: Set<String>) {
    flexSyketilfelleMockRestServiceServer
        .expect(requestTo("http://flex-syketilfelle/api/v1/ventetid/${sykmeldingIder.first()}/perioderMedSammeVentetid"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                objectMapper.writeValueAsBytes(
                    SammeVentetidResponse(
                        ventetidPerioder =
                            sykmeldingIder.map {
                                SammeVentetidPeriode(
                                    ressursId = it,
                                    ventetid =
                                        Periode(
                                            fom = LocalDate.now(), // TODO now
                                            tom = LocalDate.now().plusDays(10),
                                        ),
                                )
                            },
                    ),
                ),
                MediaType.APPLICATION_JSON,
            ),
        )
}

fun FellesTestOppsett.mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidKasterFeil(sykmeldingIder: Set<String>) {
    flexSyketilfelleMockRestServiceServer
        .expect(requestTo("http://flex-syketilfelle/api/v1/ventetid/${sykmeldingIder.first()}/perioderMedSammeVentetid"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(withServerError())
}

fun FellesTestOppsett.mockFlexSyketilfelleHentSykmeldingerMedSammeVentetidDefault(sykmeldingId: String) {
    flexSyketilfelleMockRestServiceServer
        .expect(requestTo("http://flex-syketilfelle/api/v1/ventetid/$sykmeldingId/perioderMedSammeVentetid"))
        .andExpect(method(HttpMethod.POST))
        .andRespond(
            withSuccess(
                objectMapper.writeValueAsBytes(
                    SammeVentetidResponse(ventetidPerioder = emptyList()),
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
            return "$baseUrl&andreKorrigerteRessurser=$andreKorrigerteRessurser"
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
