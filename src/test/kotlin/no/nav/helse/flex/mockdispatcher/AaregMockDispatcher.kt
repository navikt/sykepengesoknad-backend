package no.nav.helse.flex.mockdispatcher

import mockwebserver3.RecordedRequest
import no.nav.helse.flex.client.aareg.*
import no.nav.helse.flex.util.objectMapper
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate
import java.time.LocalDateTime

object AaregMockDispatcher : FellesQueueDispatcher<List<Arbeidsforhold>>(
    defaultFactory = { request: RecordedRequest ->
        val req: ArbeidsforholdRequest = objectMapper.readValue(request.body!!.utf8())
        when (val fnr = req.arbeidstakerId) {
            "22222220001" -> {
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
                )
            }

            "44444444999" -> {
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
                )
            }

            else -> emptyList()
        }
    },
)

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
