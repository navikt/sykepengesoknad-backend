package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
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
class NyttArbeidsforholdMedSluttdatoTest : FellesTestOppsett() {
    val fnr = "22222220001"

    @Test
    @Order(1)
    fun `første sykm opprettes for en lang sykmelding`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-tilkommen-inntekt")

        repeat(2) {
            AaregMockDispatcher.queuedArbeidsforholdOversikt.add(
                listOf(
                    skapArbeidsforholdOversikt(
                        fnr = fnr,
                        startdato = LocalDate.of(2022, 8, 24),
                        sluttdato = LocalDate.of(2022, 8, 25),
                        arbeidssted = "999888777",
                        opplysningspliktigOrganisasjonsnummer = "123456789",
                    ),
                ),
            )
        }

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2022, 8, 1),
                        tom = LocalDate.of(2022, 8, 25),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
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
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0", "NEI")
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)
        val antallVurderingerFraSoknader = 2
        val antallVurderingerFraSyketilfelle = 1
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = antallVurderingerFraSyketilfelle + antallVurderingerFraSoknader)
    }

    @Test
    @Order(3)
    fun `Arbeidstakersøknader opprettes for en lang sykmelding med enablet tilkommen`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2022, 8, 26),
                        tom = LocalDate.of(2022, 9, 15),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
            ),
            oppfolgingsdato = LocalDate.of(2022, 8, 1),
        )
    }

    @Test
    @Order(4)
    fun `Har ikke forventa nytt arbeidsforhold førstegangsspørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).filter { it.status == RSSoknadstatus.NY }.first()
        soknaden.sporsmal!!
            .find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0"
            }.shouldBeNull()
    }
}
