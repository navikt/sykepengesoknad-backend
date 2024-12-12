package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.client.aareg.ArbeidsforholdoversiktResponse
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.mockdispatcher.AaregMockDispatcher
import no.nav.helse.flex.mockdispatcher.skapArbeidsforholdOversikt
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforholdNyISammeSomOriginalenTest : FellesTestOppsett() {
    val fnr = "22222220001"

    @Test
    @Order(1)
    fun `første sykm opprettes for en lang sykmelding`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-tilkommen-inntekt")

        repeat(2, {
            AaregMockDispatcher.queuedArbeidsforholdOversikt.add(
                ArbeidsforholdoversiktResponse(
                    arbeidsforholdoversikter =
                        listOf(
                            skapArbeidsforholdOversikt(
                                fnr = fnr,
                                startdato = LocalDate.of(2022, 8, 24),
                                sluttdato = LocalDate.of(2022, 8, 25),
                                arbeidssted = "999888777",
                                opplysningspliktigOrganisasjonsnummer = "123456789",
                            ),
                        ),
                ),
            )
        })

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
    fun `Vi besvarer og sender inn første søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).sortedBy { it.fom }.first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(NYTT_ARBEIDSFORHOLD_UNDERVEIS, "NEI")
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }


}
