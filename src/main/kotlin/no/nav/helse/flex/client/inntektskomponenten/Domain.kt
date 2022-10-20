package no.nav.helse.flex.client.inntektskomponenten

import java.time.YearMonth

data class HentInntekterRequest(

    val ident: Aktoer,
    val ainntektsfilter: String,
    val formaal: String,
    val maanedFom: YearMonth,
    val maanedTom: YearMonth
)

data class HentInntekterResponse(
    val arbeidsInntektMaaned: List<ArbeidsInntektMaaned> = emptyList(),
    val ident: Aktoer
)

data class Aktoer(
    val identifikator: String,
    val aktoerType: String
)

data class InntektListe(
    val inntektType: String,
    val virksomhet: Aktoer,
)

data class ArbeidsforholdFrilanser(
    val arbeidsforholdstype: String,
    val arbeidsgiver: Aktoer
)

data class ArbeidsInntektMaaned(
    val aarMaaned: String,
    val arbeidsInntektInformasjon: ArbeidsInntektInformasjon
)

data class ArbeidsInntektInformasjon(
    val inntektListe: List<InntektListe> = emptyList(),
    val arbeidsforholdListe: List<ArbeidsforholdFrilanser> = emptyList()
)
