package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.aareg.*
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.time.LocalDate

object AaregMockDispatcher : Dispatcher() {
    val queuedArbeidsforholdOversikt = mutableListOf<ArbeidsforholdoversiktResponse>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (queuedArbeidsforholdOversikt.isEmpty()) {
            val req: ArbeidsforholdRequest = objectMapper.readValue(request.body.readUtf8())
            when (val fnr = req.arbeidstakerId) {
                "22222220001" -> {
                    return ArbeidsforholdoversiktResponse(
                        arbeidsforholdoversikter =
                            listOf(
                                skapArbeidsforholdOversikt(
                                    fnr = fnr,
                                    startdato = LocalDate.of(2022, 9, 15).minusDays(40),
                                    arbeidssted = "999333666",
                                    opplysningspliktigOrganisasjonsnummer = "11224455441",
                                    sluttdato = LocalDate.of(2022, 9, 15).minusDays(30),
                                ),
                                skapArbeidsforholdOversikt(
                                    fnr = fnr,
                                    startdato = LocalDate.of(2022, 9, 15).minusDays(10),
                                    arbeidssted = "999888777",
                                    opplysningspliktigOrganisasjonsnummer = "11224455441",
                                ),
                            ),
                    ).tilMockResponse()
                }

                "44444444999" -> {
                    return ArbeidsforholdoversiktResponse(
                        arbeidsforholdoversikter =
                            listOf(
                                skapArbeidsforholdOversikt(
                                    fnr = fnr,
                                    startdato = LocalDate.of(2022, 9, 15).minusDays(40),
                                    arbeidssted = "999333666",
                                    opplysningspliktigOrganisasjonsnummer = "1984108765",
                                    sluttdato = null,
                                ),
                                skapArbeidsforholdOversikt(
                                    fnr = fnr,
                                    startdato = LocalDate.of(2022, 9, 15).minusDays(10),
                                    arbeidssted = "999888777",
                                    opplysningspliktigOrganisasjonsnummer = "1984108765",
                                ),
                            ),
                    ).tilMockResponse()
                }

                else -> return ArbeidsforholdoversiktResponse(arbeidsforholdoversikter = emptyList()).tilMockResponse()
            }
        }
        val poppedElement = queuedArbeidsforholdOversikt.removeAt(YrkesskadeMockDispatcher.queuedSakerRespons.size)

        return MockResponse()
            .setResponseCode(200)
            .setBody(
                poppedElement.serialisertTilString(),
            )
            .addHeader("Content-Type", "application/json")
    }

    private fun ArbeidsforholdoversiktResponse.tilMockResponse(): MockResponse {
        return MockResponse().setBody(this.serialisertTilString()).addHeader("Content-Type", "application/json")
    }
}

fun skapArbeidsforholdOversikt(
    startdato: LocalDate = LocalDate.of(2022, 9, 15).minusDays(10),
    sluttdato: LocalDate? = null,
    arbeidssted: String,
    fnr: String,
    opplysningspliktigOrganisasjonsnummer: String? = null,
): ArbeidsforholdOversikt {
    return ArbeidsforholdOversikt(
        type = Kodeverksentitet("ordinaertArbeidsforhold", "Ordinært arbeidsforhold"),
        arbeidstaker = Arbeidstaker(listOf(Ident("FOLKEREGISTERIDENT", fnr, true))),
        arbeidssted = Arbeidssted("Underenhet", listOf(Ident("ANNENTYPE", "123123123"),Ident("ORGANISASJONSNUMMER", arbeidssted))),
        opplysningspliktig =
            Opplysningspliktig(
                "Hovedenhet",
                listOf(Ident("ORGANISASJONSNUMMER", opplysningspliktigOrganisasjonsnummer ?: tilfeldigOrgNummer())),
            ),
        startdato = startdato,
        sluttdato = sluttdato,
        yrke = Kodeverksentitet("1231119", "KONTORLEDER"),
        avtaltStillingsprosent = 100,
        permisjonsprosent = 0,
        permitteringsprosent = 0,
    )
}

fun tilfeldigOrgNummer(): String {
    var orgNummer = ""
    repeat(9) { orgNummer += (1..10).random().toString() }
    return orgNummer
}
