package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforholdMuteringTest : NyttArbeidsforholdFellesOppsett() {
    @Test
    @Order(2)
    fun `Har forventa nytt arbeidsforhold førstegangsspørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS)
            }!!
        @Suppress("ktlint:standard:max-line-length")
        nyttArbeidsforholdSpm.sporsmalstekst `should be equal to`
            "Har du jobbet noe hos Kiosken, avd Oslo AS i perioden 5. - 15. september 2022?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.metadata!!.get("startdatoAareg").textValue() `should be equal to` "2022-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("fom").textValue() `should be equal to` "2022-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("tom").textValue() `should be equal to` "2022-09-15"
    }

    @Test
    @Order(3)
    fun `Svarer tilbake i arbeid dag 1`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .tilbakeIArbeid(soknaden.fom!!)
    }

    @Test
    @Order(4)
    fun `Spørsmålet er mutert bort`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        soknaden.sporsmal!!
            .find {
                it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS)
            }.shouldBeNull()
    }

    @Test
    @Order(5)
    fun `Svarer tilbake i arbeid dag 2`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .tilbakeIArbeid(soknaden.fom!!.plusDays(1))
    }

    @Test
    @Order(6)
    fun `Spørsmålet er fortsatt mutert bort fordi det var før startdatoen`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        soknaden.sporsmal!!
            .find {
                it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS)
            }.shouldBeNull()
    }

    @Test
    @Order(7)
    fun `Svarer tilbake i arbeid dagen etter startdatoen`() {
        val soknaden = hentSoknader(fnr = fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .tilbakeIArbeid(LocalDate.of(2022, 9, 6))
    }

    @Test
    @Order(8)
    fun `Spørsmålet er tilbake`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS)
            }!!
        @Suppress("ktlint:standard:max-line-length")
        nyttArbeidsforholdSpm.sporsmalstekst `should be equal to`
            "Har du jobbet noe hos Kiosken, avd Oslo AS i perioden 5. - 5. september 2022?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.metadata!!.get("startdatoAareg").textValue() `should be equal to` "2022-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("fom").textValue() `should be equal to` "2022-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("tom").textValue() `should be equal to` "2022-09-05"
    }

    @Test
    @Order(9)
    fun `Svarer tilbake i arbeid på startdatoen`() {
        val soknaden = hentSoknader(fnr = fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .tilbakeIArbeid(LocalDate.of(2022, 9, 5))
    }

    @Test
    @Order(10)
    fun `Spørsmålet er ikke der på startdatoen`() {
        val soknaden = hentSoknader(fnr = fnr).first()
        soknaden.sporsmal!!
            .find {
                it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS)
            }.shouldBeNull()
    }

    @Test
    @Order(11)
    fun `Svarer ikke tilbake i arbeid`() {
        val soknaden = hentSoknader(fnr = fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .tilbakeIArbeid(dato = null)
    }

    @Test
    @Order(12)
    fun `Er tilbake på originalt spørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS)
            }!!
        @Suppress("ktlint:standard:max-line-length")
        nyttArbeidsforholdSpm.sporsmalstekst `should be equal to`
            "Har du jobbet noe hos Kiosken, avd Oslo AS i perioden 5. - 15. september 2022?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.metadata!!.get("startdatoAareg").textValue() `should be equal to` "2022-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("fom").textValue() `should be equal to` "2022-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("tom").textValue() `should be equal to` "2022-09-15"
    }

    @Test
    @Order(13)
    fun `Svarer tilbake i arbeid dagen etter startdatoen før innsending`() {
        val soknaden = hentSoknader(fnr = fnr).first()
        SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
            .tilbakeIArbeid(LocalDate.of(2022, 9, 6))
    }

    @Test
    @Order(14)
    fun `Sender søknaden, tom på kafka er blitt forkortet`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
                .standardSvar(ekskludert = listOf(TILBAKE_I_ARBEID))
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0", svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO + "0",
                    svar = "400000",
                    ferdigBesvart = true,
                ).sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom.toString() `should be equal to` "2022-09-05"
        inntektFraNyttArbeidsforhold.tom.toString() `should be equal to` "2022-09-05"
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer `should be equal to` "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer `should be equal to` "11224455441"

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
