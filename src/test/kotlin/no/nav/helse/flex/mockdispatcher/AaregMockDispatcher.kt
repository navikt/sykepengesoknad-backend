package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.aareg.*
import no.nav.helse.flex.client.inntektskomponenten.*
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.QueueDispatcher
import okhttp3.mockwebserver.RecordedRequest
import java.time.LocalDate

object AaregMockDispatcher : QueueDispatcher() {
    override fun dispatch(request: RecordedRequest): MockResponse {
        val req: ArbeidsforholdRequest = objectMapper.readValue(request.body.readUtf8())
        val fnr = req.arbeidstakerId
        if (fnr == "22222220001") {
            return ArbeidsforholdoversiktResponse(
                arbeidsforholdoversikter =
                    listOf(
                        skapArbeidsforholdOversikt(fnr = fnr),
                    ),
            ).tilMockResponse()
        }

        return ArbeidsforholdoversiktResponse(arbeidsforholdoversikter = emptyList()).tilMockResponse()
    }

    fun ArbeidsforholdoversiktResponse.tilMockResponse(): MockResponse {
        return MockResponse().setBody(this.serialisertTilString()).addHeader("Content-Type", "application/json")
    }
}

fun skapArbeidsforholdOversikt(
    startdato: LocalDate = LocalDate.now().minusDays(10),
    sluttdato: LocalDate? = null,
    fnr: String,
): ArbeidsforholdOversikt {
    return ArbeidsforholdOversikt(
        type = Kodeverksentitet("ORDINERT", "Ordinært arbeidsforhold"),
        arbeidstaker = Arbeidstaker(listOf(Ident("FNR", fnr, true))),
        arbeidssted = Arbeidssted("VIRKSOMHET", listOf(Ident("ORGNR", "999333666", true))),
        opplysningspliktig = Opplysningspliktig("VIRKSOMHET", listOf(Ident("ORGNR", "999333666", true))),
        startdato = startdato,
        sluttdato = sluttdato,
        yrke = Kodeverksentitet("1234", "Kodeverksentitet"),
        avtaltStillingsprosent = 100,
        permisjonsprosent = 0,
        permitteringsprosent = 0,
    )
}
