package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.client.aareg.Kodeverksentitet
import no.nav.helse.flex.mockdispatcher.AaregMockDispatcher
import no.nav.helse.flex.mockdispatcher.skapArbeidsforholdOversikt
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.*
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VidereføringMestSannsynligTest : FellesTestOppsett() {
    val fnr = "22222220001"

    @Test
    @Order(1)
    fun `Videreføring med mest sannsynlig kravene tilfredsstilt`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-tilkommen-inntekt")

        AaregMockDispatcher.enqueue(
            listOf(
                skapArbeidsforholdOversikt(
                    fnr = fnr,
                    startdato = LocalDate.of(2018, 8, 1),
                    sluttdato = LocalDate.of(2022, 8, 23),
                    sluttaarsak =
                        Kodeverksentitet(
                            "endringIOrganisasjonsstrukturEllerByttetJobbInternt",
                            "Endring i organisasjonsstruktur eller byttet jobb internt",
                        ),
                    arbeidssted = "999888777",
                    opplysningspliktigOrganisasjonsnummer = "888888888",
                ),
                skapArbeidsforholdOversikt(
                    fnr = fnr,
                    startdato = LocalDate.of(2022, 8, 24),
                    sluttdato = LocalDate.of(2022, 8, 25),
                    arbeidssted = "999888722",
                    opplysningspliktigOrganisasjonsnummer = "888888888",
                ),
            ),
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2022, 8, 1),
                        tom = LocalDate.of(2022, 8, 25),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "999888777", orgNavn = "MATBUTIKKEN AS"),
            ),
            oppfolgingsdato = LocalDate.of(2022, 8, 1),
        )
    }

    @Test
    @Order(2)
    fun `Skal ikke ha tilkommen inntekt spørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).sortedBy { it.fom }.first()
        soknaden.sporsmal!!
            .find {
                it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS)
            }.shouldBeNull()
    }
}
