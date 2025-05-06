package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.inntektskomponenten.Aktoer
import no.nav.helse.flex.client.inntektskomponenten.ArbeidsInntektInformasjon
import no.nav.helse.flex.client.inntektskomponenten.ArbeidsInntektMaaned
import no.nav.helse.flex.client.inntektskomponenten.ArbeidsforholdFrilanser
import no.nav.helse.flex.client.inntektskomponenten.HentInntekterRequest
import no.nav.helse.flex.client.inntektskomponenten.HentInntekterResponse
import no.nav.helse.flex.client.inntektskomponenten.InntektListe
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

object InntektskomponentenMockDispatcher : Dispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val req: HentInntekterRequest = objectMapper.readValue(request.body.readUtf8())
        if (req.ident.identifikator == "11111234565") {
            return HentInntekterResponse(
                arbeidsInntektMaaned =
                    listOf(
                        ArbeidsInntektMaaned(
                            arbeidsInntektInformasjon =
                                ArbeidsInntektInformasjon(
                                    arbeidsforholdListe =
                                        listOf(
                                            ArbeidsforholdFrilanser(
                                                arbeidsforholdstype = "frilanserOppdragstakerHonorarPersonerMm",
                                                arbeidsgiver = Aktoer("999333667", "ORGANISASJON"),
                                            ),
                                        ),
                                    inntektListe =
                                        listOf(
                                            InntektListe(
                                                inntektType = "LOENNSINNTEKT",
                                                virksomhet = Aktoer("999333666", "ORGANISASJON"),
                                            ),
                                            InntektListe(
                                                inntektType = "LOENNSINNTEKT",
                                                virksomhet = Aktoer("999333667", "ORGANISASJON"),
                                            ),
                                        ),
                                ),
                        ),
                        ArbeidsInntektMaaned(
                            arbeidsInntektInformasjon =
                                ArbeidsInntektInformasjon(
                                    inntektListe =
                                        listOf(
                                            InntektListe(
                                                inntektType = "LOENNSINNTEKT",
                                                virksomhet = Aktoer("999333666", "ORGANISASJON"),
                                            ),
                                            InntektListe(
                                                inntektType = "LOENNSINNTEKT",
                                                virksomhet = Aktoer("999333667", "ORGANISASJON"),
                                            ),
                                        ),
                                ),
                        ),
                    ),
                ident = req.ident,
            ).tilMockResponse()
        }
        if (req.ident.identifikator == "22222222222") {
            return HentInntekterResponse(
                arbeidsInntektMaaned =
                    listOf(
                        ArbeidsInntektMaaned(
                            arbeidsInntektInformasjon =
                                ArbeidsInntektInformasjon(
                                    inntektListe =
                                        listOf(
                                            InntektListe(
                                                inntektType = "LOENNSINNTEKT",
                                                virksomhet = Aktoer("999333666", "ORGANISASJON"),
                                            ),
                                            InntektListe(
                                                inntektType = "LOENNSINNTEKT",
                                                virksomhet = Aktoer("999888777", "ORGANISASJON"),
                                            ),
                                        ),
                                ),
                        ),
                    ),
                ident = req.ident,
            ).tilMockResponse()
        }
        if (req.ident.identifikator == "3333333333") {
            return HentInntekterResponse(
                arbeidsInntektMaaned =
                    listOf(
                        ArbeidsInntektMaaned(
                            arbeidsInntektInformasjon =
                                ArbeidsInntektInformasjon(
                                    arbeidsforholdListe =
                                        listOf(
                                            ArbeidsforholdFrilanser(
                                                arbeidsforholdstype = "frilanserOppdragstakerHonorarPersonerMm",
                                                arbeidsgiver = Aktoer("999333666", "ORGANISASJON"),
                                            ),
                                        ),
                                    inntektListe =
                                        listOf(
                                            InntektListe(
                                                inntektType = "LOENNSINNTEKT",
                                                virksomhet = Aktoer("999333666", "ORGANISASJON"),
                                            ),
                                        ),
                                ),
                        ),
                    ),
                ident = req.ident,
            ).tilMockResponse()
        }
        return HentInntekterResponse(arbeidsInntektMaaned = emptyList(), ident = req.ident).tilMockResponse()
    }

    fun HentInntekterResponse.tilMockResponse(): MockResponse =
        MockResponse().setBody(this.serialisertTilString()).addHeader("Content-Type", "application/json")
}
