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
                            ),
                            skapArbeidsforholdOversikt(
                                fnr = fnr,
                                startdato = LocalDate.of(2022, 9, 15).minusDays(10),
                                arbeidssted = "999888777",
                                opplysningspliktigOrganisasjonsnummer = "1984108765",
                            ),
                            skapArbeidsforholdOversikt(
                                fnr = fnr,
                                startdato = LocalDate.of(2022, 9, 15).minusDays(10),
                                arbeidssted = "999333667",
                            ),
                        ),
                ).tilMockResponse()
            }
            else -> return ArbeidsforholdoversiktResponse(arbeidsforholdoversikter = emptyList()).tilMockResponse()
        }
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
        arbeidssted = Arbeidssted("Underenhet", listOf(Ident("ORGANISASJONSNUMMER", arbeidssted))),
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
