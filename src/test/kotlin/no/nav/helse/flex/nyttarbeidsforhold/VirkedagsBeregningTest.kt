package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class VirkedagsBeregningTest : NyttArbeidsforholdFellesOppsett() {
    @Test
    @Order(2)
    fun `Vi svarer ferie hele perioden og ender med ingen virkedager`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO, svar = "400000", ferdigBesvart = true)
                .medFerie(fom = soknaden.fom!!, tom = soknaden.tom!!)
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` basisdato.minusDays(20)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato
        inntektFraNyttArbeidsforhold.belopPerDag `should be equal to` 0
        inntektFraNyttArbeidsforhold.virkedager `should be equal to` 0

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(3)
    fun `Vi svarer ferie nesten hele perioden, det er helg i slutten og ender med ingen virkedager`() {
        val forrigeSoknad = hentSoknader(fnr = fnr).first()
        val soknaden = korrigerSoknad(soknadId = forrigeSoknad.id, fnr = fnr)
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = forrigeSoknad.id)

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO, svar = "400000", ferdigBesvart = true)
                .medFerie(fom = soknaden.fom!!, tom = soknaden.tom!!.minusDays(2))
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` basisdato.minusDays(20)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato
        inntektFraNyttArbeidsforhold.belopPerDag `should be equal to` 0
        inntektFraNyttArbeidsforhold.virkedager `should be equal to` 0

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(4)
    fun `Vi svarer ferie nesten hele perioden, det er fredag og helg i slutten og ender med 1 virkedag`() {
        val forrigeSoknad = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.SENDT }
        val soknaden = korrigerSoknad(soknadId = forrigeSoknad.id, fnr = fnr)
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = forrigeSoknad.id)

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO, svar = "400000", ferdigBesvart = true)
                .medFerie(fom = soknaden.fom!!, tom = soknaden.tom!!.minusDays(3))
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` basisdato.minusDays(20)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato
        inntektFraNyttArbeidsforhold.belopPerDag `should be equal to` 4000
        inntektFraNyttArbeidsforhold.virkedager `should be equal to` 1

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
