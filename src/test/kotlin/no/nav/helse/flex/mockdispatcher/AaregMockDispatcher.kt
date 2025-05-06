package no.nav.helse.flex.mockdispatcher

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.client.aareg.*
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.time.LocalDate
import java.time.LocalDateTime

object AaregMockDispatcher : Dispatcher() {
    val queuedArbeidsforholdOversikt = mutableListOf<List<Arbeidsforhold>>()

    override fun dispatch(request: RecordedRequest): MockResponse {
        if (queuedArbeidsforholdOversikt.isEmpty()) {
            val req: ArbeidsforholdRequest = objectMapper.readValue(request.body.readUtf8())
            when (val fnr = req.arbeidstakerId) {
                "22222220001" -> {
                    return listOf(
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
                    ).tilMockResponse()
                }

                "44444444999" -> {
                    return listOf(
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
                    ).tilMockResponse()
                }

                else -> return emptyList<Arbeidsforhold>().tilMockResponse()
            }
        }
        val poppedElement = queuedArbeidsforholdOversikt.removeAt(YrkesskadeMockDispatcher.queuedSakerRespons.size)

        return MockResponse()
            .setResponseCode(200)
            .setBody(
                poppedElement.serialisertTilString(),
            ).addHeader("Content-Type", "application/json")
    }

    private fun List<Arbeidsforhold>.tilMockResponse(): MockResponse =
        MockResponse().setBody(this.serialisertTilString()).addHeader("Content-Type", "application/json")
}

fun skapArbeidsforholdOversikt(
    startdato: LocalDate = LocalDate.of(2022, 9, 15).minusDays(10),
    sluttdato: LocalDate? = null,
    sluttaarsak: Kodeverksentitet? = null,
    arbeidssted: String,
    fnr: String,
    opprettet: LocalDateTime = LocalDateTime.now(),
    opplysningspliktigOrganisasjonsnummer: String? = null,
    ansettelsesdetaljer: List<Ansettelsesdetaljer> = emptyList(),
): Arbeidsforhold =
    Arbeidsforhold(
        type = Kodeverksentitet("ordinaertArbeidsforhold", "Ordinært arbeidsforhold"),
        arbeidstaker = Arbeidstaker(listOf(Ident("FOLKEREGISTERIDENT", fnr, true))),
        arbeidssted = Arbeidssted("Underenhet", listOf(Ident("ORGANISASJONSNUMMER", arbeidssted))),
        opplysningspliktig =
            Opplysningspliktig(
                "Hovedenhet",
                listOf(Ident("ORGANISASJONSNUMMER", opplysningspliktigOrganisasjonsnummer ?: tilfeldigOrgNummer())),
            ),
        ansettelsesdetaljer = ansettelsesdetaljer,
        opprettet = opprettet,
        ansettelsesperiode =
            Ansettelsesperiode(
                startdato = startdato,
                sluttdato = sluttdato,
                sluttaarsak = sluttaarsak,
            ),
    )

fun tilfeldigOrgNummer(): String {
    var orgNummer = ""
    repeat(9) { orgNummer += (1..10).random().toString() }
    return orgNummer
}
