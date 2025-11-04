package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforholdMedNyttSkjaringstidspunktTest : NyttArbeidsforholdFellesOppsett() {
    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.ventPåRecords(3)
    }

    @Test
    @Order(2)
    fun `Vi besvarer og sender inn søknade med ja på første`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
                .tilbakeIArbeid(dato = basisdato.minusDays(3))
                .standardSvar(ekskludert = listOf(TILBAKE_I_ARBEID))
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0", svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO + "0", svar = "400000", ferdigBesvart = true)
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` LocalDate.of(2022, 9, 5)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato.minusDays(4)
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer `should be equal to` "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer `should be equal to` "11224455441"
    }

    @Test
    @Order(3)
    fun `Sykmeldingen forlenges`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2022, 9, 16),
                        tom = LocalDate.of(2022, 10, 6),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
            ),
            oppfolgingsdato = basisdato.minusDays(20),
        )
    }

    @Test
    @Order(4)
    fun `Har ikke nytt arbeidsforhold spørsmål etter nytt skjæringstidspunkt`() {
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.NY }

        soknaden.sporsmal!!
            .find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0"
            }.shouldBeNull()
    }
}
