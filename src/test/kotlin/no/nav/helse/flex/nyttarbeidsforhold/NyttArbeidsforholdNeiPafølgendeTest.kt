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

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforholdNeiPafølgendeTest : NyttArbeidsforholdFellesOppsett() {
    @Test
    @Order(2)
    fun `Vi besvarer og sender inn søknade med ja på første`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO, svar = "400000", ferdigBesvart = true)
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` basisdato.minusDays(20)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer `should be equal to` "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer `should be equal to` "11224455441"

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(3)
    fun `Sykmeldingen forlenges`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.plusDays(1),
                        tom = basisdato.plusDays(21),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
            ),
            oppfolgingsdato = basisdato.minusDays(20),
        )
    }

    @Test
    @Order(4)
    fun `Har forventa nytt arbeidsforhold påfølgende spørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.NY }

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS
            }!!
        nyttArbeidsforholdSpm.sporsmalstekst!!.shouldContain("Har du jobbet noe hos Kiosken, avd Oslo AS i perioden")
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
    }

    @Test
    @Order(5)
    fun `Vi besvarer og sender inn den andre søknaden med nei på påfølgende`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.NY }

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS, svar = "NEI")
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first().harJobbet.`should be false`()
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first().belop.`should be null`()

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
