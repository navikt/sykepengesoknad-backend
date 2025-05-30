package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.hentProduserteRecords
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS
import no.nav.helse.flex.soknadsopprettelse.NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforhold0SvarTest : NyttArbeidsforholdFellesOppsett() {
    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(2)
    fun `Vi besvarer og sender inn søknaden med 0 svar`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0", svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO + "0", svar = "0", ferdigBesvart = true)
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` LocalDate.of(2022, 9, 5)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato
    }
}
