package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.aareg.*
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
                        skapArbeidsforholdOversikt(
                            fnr = fnr,
                            startdato = LocalDate.of(2024, 9, 15).minusDays(40),
                            arbeidssted = "999333666",
                        ),
                        skapArbeidsforholdOversikt(
                            fnr = fnr,
                            startdato = LocalDate.of(2024, 9, 15).minusDays(10),
                            arbeidssted = "999888777",
                        ),
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
    startdato: LocalDate = LocalDate.of(2024, 9, 15).minusDays(10),
    sluttdato: LocalDate? = null,
    arbeidssted: String,
    fnr: String,
): ArbeidsforholdOversikt {
    return ArbeidsforholdOversikt(
        type = Kodeverksentitet("ordinaertArbeidsforhold", "Ordinært arbeidsforhold"),
        arbeidstaker = Arbeidstaker(listOf(Ident("FOLKEREGISTERIDENT", fnr, true))),
        arbeidssted = Arbeidssted("Underenhet", listOf(Ident("ORGANISASJONSNUMMER", arbeidssted))),
        opplysningspliktig = Opplysningspliktig("Hovedenhet", listOf(Ident("ORGANISASJONSNUMMER", "11224455441"))),
        startdato = startdato,
        sluttdato = sluttdato,
        yrke = Kodeverksentitet("1231119", "KONTORLEDER"),
        avtaltStillingsprosent = 100,
        permisjonsprosent = 0,
        permitteringsprosent = 0,
    )
}
