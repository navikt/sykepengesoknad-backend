package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.Kilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.KjentInntektskilde
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
class NyttArbeidsforholdTest : NyttArbeidsforholdFellesOppsett() {
    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(2)
    fun `Har forventa nytt arbeidsforhold førstegangsspørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0"
            }!!
        @Suppress("ktlint:standard:max-line-length")
        nyttArbeidsforholdSpm.sporsmalstekst `should be equal to`
            "Har du jobbet noe hos Kiosken, avd Oslo AS i perioden 5. - 15. september 2022?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.metadata!!.get("startdatoAareg").textValue() `should be equal to` "2022-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("fom").textValue() `should be equal to` "2022-09-05"
        nyttArbeidsforholdSpm.metadata!!.get("tom").textValue() `should be equal to` "2022-09-15"
        nyttArbeidsforholdSpm.undersporsmal.map { it.tag } `should be equal to`
            listOf(
                NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO + "0",
            )
        soknaden.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0",
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )
    }

    @Test
    @Order(2)
    fun `Vi har lagret dataene på forventet format i databasen`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val sykepengesoknadDbRecord = sykepengesoknadRepository.findBySykepengesoknadUuid(soknaden.id)!!
        @Suppress("ktlint:standard:max-line-length")
        sykepengesoknadDbRecord.arbeidsforholdFraAareg `should be equal to`
            """[{"opplysningspliktigOrgnummer":"11224455441","arbeidsstedOrgnummer":"999888777","arbeidsstedNavn":"Kiosken, avd Oslo AS","startdato":"2022-09-05","sluttdato":null}]"""
    }

    @Test
    @Order(3)
    fun `Vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0", svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO + "0", svar = "400000", ferdigBesvart = true)
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val andreInntektskilder = soknaden.sporsmal!!.first { it.tag == "ANDRE_INNTEKTSKILDER_V2" }
        val andreInntektskilderMetadata =
            andreInntektskilder.metadata!!.tilAndreInntektskilderMetadata()

        andreInntektskilderMetadata.kjenteInntektskilder `should be equal to`
            listOf(
                KjentInntektskilde(
                    navn = "Matbutikken AS",
                    kilde = Kilde.SYKMELDING,
                    orgnummer = "123454543",
                ),
                KjentInntektskilde(
                    navn = "Kiosken, avd Oslo AS",
                    kilde = Kilde.AAAREG,
                    orgnummer = "999888777",
                ),
            )
        andreInntektskilder.sporsmalstekst `should be equal to` "Har du andre inntektskilder enn Matbutikken AS og Kiosken, avd Oslo AS?"

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` LocalDate.of(2022, 9, 5)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer `should be equal to` "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer `should be equal to` "11224455441"
    }

    @Test
    @Order(4)
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
    @Order(5)
    fun `Har forventa nytt arbeidsforhold påfølgende spørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.NY }

        val nyttArbeidsforholdSpm =
            soknaden.sporsmal!!.find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0"
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
                NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0",
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
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0", svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO + "0", svar = "400000", ferdigBesvart = true)
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom `should be equal to` basisdato.plusDays(1)
        inntektFraNyttArbeidsforhold.tom `should be equal to` basisdato.plusDays(21)
        inntektFraNyttArbeidsforhold.belop `should be equal to` 4000
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer `should be equal to` "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer `should be equal to` "11224455441"
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

        hentSoknader(fnr = fnr)
            .flatMap { it.sporsmal!! }
            .filter { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS }
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

        hentSoknader(fnr = fnr)
            .flatMap { it.sporsmal!! }
            .filter { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS }
            .`should be empty`()
    }
}
