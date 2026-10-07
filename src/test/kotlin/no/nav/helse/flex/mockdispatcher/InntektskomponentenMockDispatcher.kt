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
import okhttp3.mockwebserver.RecordedRequest

object InntektskomponentenMockDispatcher : FellesQueueDispatcher<HentInntekterResponse>(
    defaultFactory = { request: RecordedRequest ->
        val req: HentInntekterRequest = objectMapper.readValue(request.body.readUtf8())
        HentInntekterResponse(arbeidsInntektMaaned = emptyList(), ident = req.ident)
    },
)

fun skapHentInntekterResponse(
    fnr: String,
    loennsinntektOrgnumre: List<String> = emptyList(),
    frilansOrgnumre: List<String> = emptyList(),
    ordinaereArbeidsforholdOrgnumre: List<String> = emptyList(),
): HentInntekterResponse =
    HentInntekterResponse(
        arbeidsInntektMaaned =
            listOf(
                ArbeidsInntektMaaned(
                    arbeidsInntektInformasjon =
                        ArbeidsInntektInformasjon(
                            inntektListe =
                                loennsinntektOrgnumre.map {
                                    InntektListe(
                                        inntektType = "LOENNSINNTEKT",
                                        virksomhet = Aktoer(it, "ORGANISASJON"),
                                    )
                                },
                            arbeidsforholdListe =
                                frilansOrgnumre.map {
                                    ArbeidsforholdFrilanser(
                                        arbeidsforholdstype = "frilanserOppdragstakerHonorarPersonerMm",
                                        arbeidsgiver = Aktoer(it, "ORGANISASJON"),
                                    )
                                } +
                                    ordinaereArbeidsforholdOrgnumre.map {
                                        ArbeidsforholdFrilanser(
                                            arbeidsforholdstype = "ordinaertArbeidsforhold",
                                            arbeidsgiver = Aktoer(it, "ORGANISASJON"),
                                        )
                                    },
                        ),
                ),
            ),
        ident = Aktoer(fnr, "NATURLIG_IDENT"),
    )

fun skapArbeidstakerOgFrilanserInntekter(fnr: String): HentInntekterResponse =
    skapHentInntekterResponse(
        fnr = fnr,
        loennsinntektOrgnumre = listOf("999333666", "999333667"),
        frilansOrgnumre = listOf("999333667"),
    )
