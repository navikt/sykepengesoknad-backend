package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
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
class NyttArbeidsforholdTest : NyttArbeidsforholdFellesOppsett() {
    @Test
    @Order(2)
    fun `Har forventa nytt arbeidsforhold førstegangsspørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG
            }!!
        nyttArbeidsforholdSpm.sporsmalstekst `should be equal to` "Har du startet å jobbe hos Kiosken, avd Oslo AS?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.undersporsmal.map { it.tag } `should be equal to`
            listOf(
                NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG_FORSTE_ARBEIDSDAG,
                NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO,
            )
        soknaden.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG,
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )
    }

    @Test
    @Order(3)
    fun `Vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG_FORSTE_ARBEIDSDAG,
                    svar = basisdato.minusDays(4).toString(),
                    ferdigBesvart = false,
                )
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO, svar = "400000", ferdigBesvart = true)
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` basisdato.minusDays(4)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato
        inntektFraNyttArbeidsforhold.forstegangssporsmal.`should be true`()
        inntektFraNyttArbeidsforhold.belopPerDag `should be equal to` 1333
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer `should be equal to` "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer `should be equal to` "11224455441"
        inntektFraNyttArbeidsforhold.forsteArbeidsdag `should be equal to` basisdato.minusDays(4)

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(4)
    fun `Sykmeldingen forlenges`() {
        val soknad =
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
        soknadAktivering.aktiverSoknad(soknad.first().id)
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
    }

    @Test
    @Order(5)
    fun `Har forventa nytt arbeidsforhold påfølgende spørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.NY }

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag == "NYTT_ARBEIDSFORHOLD_UNDERVEIS_PAFOLGENDE"
            }!!
        nyttArbeidsforholdSpm.sporsmalstekst!!.shouldContain("Har du jobbet noe hos Kiosken, avd Oslo AS i perioden")
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"

        soknaden.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                NYTT_ARBEIDSFORHOLD_UNDERVEIS_PAFOLGENDE,
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )
    }

    @Test
    @Order(6)
    fun `Vi besvarer og sender inn den andre søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.NY }

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_PAFOLGENDE, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO, svar = "400000", ferdigBesvart = true)
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` basisdato.plusDays(1)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato.plusDays(21)
        inntektFraNyttArbeidsforhold.forstegangssporsmal.`should be false`()
        inntektFraNyttArbeidsforhold.belopPerDag `should be equal to` 266
        inntektFraNyttArbeidsforhold.belop `should be equal to` 4000
        inntektFraNyttArbeidsforhold.virkedager `should be equal to` 15
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer `should be equal to` "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer `should be equal to` "11224455441"
        inntektFraNyttArbeidsforhold.forsteArbeidsdag `should be equal to` basisdato.minusDays(4)

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(90)
    fun `Vi lager ikke tilkommen inntekt spm ved sykmelding fra flere arbeidsgivere`() {
        databaseReset.resetDatabase()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato,
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "999888777", orgNavn = "Kiosken, avd Oslo AS"),
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato,
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
            ),
        )

        hentSoknader(fnr = fnr).flatMap { it.sporsmal!! }.filter { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG }
            .`should be empty`()
    }

    @Test
    @Order(91)
    fun `Vi lager ikke tilkommen inntekt spm ved sykmelding fra andre arbeidssituasjoner`() {
        databaseReset.resetDatabase()
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato,
                    ),
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                arbeidsgiver = null,
            ),
        )
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato,
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
            ),
        )

        hentSoknader(fnr = fnr).flatMap { it.sporsmal!! }.filter { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG }
            .`should be empty`()
    }
}
