package no.nav.helse.flex.nyttarbeidsforhold

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.nyttarbeidsforhold.tilAndreInntektskilderMetadata
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforholdTest : NyttArbeidsforholdFellesOppsett() {
    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.ventPåRecords(6)
    }

    @Test
    @Order(2)
    fun `Har forventa nytt arbeidsforhold førstegangsspørsmål`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val nyttArbeidsforholdSpm = soknaden.getSporsmalMedTag(NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0")
        @Suppress("ktlint:standard:max-line-length")
        nyttArbeidsforholdSpm.sporsmalstekst shouldBe
            "Har du jobbet noe hos Kiosken, avd Oslo AS i perioden 5. - 15. september 2022?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").asString() shouldBe "999888777"
        nyttArbeidsforholdSpm.metadata.get("arbeidsstedNavn").asString() shouldBe "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.metadata.get("startdatoAareg").asString() shouldBe "2022-09-05"
        nyttArbeidsforholdSpm.metadata.get("fom").asString() shouldBe "2022-09-05"
        nyttArbeidsforholdSpm.metadata.get("tom").asString() shouldBe "2022-09-15"
        nyttArbeidsforholdSpm.undersporsmal.map { it.tag } shouldBe
            listOf(
                NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO + "0",
            )
        soknaden.sporsmal?.map { it.tag } shouldBe
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
        sykepengesoknadDbRecord.arbeidsforholdFraAareg shouldBe
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

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)

        kafkaSoknader[0].status shouldBe SoknadsstatusDTO.SENDT
        kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldHaveSize(1)

        val inntektFraNyttArbeidsforhold = kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.first()
        inntektFraNyttArbeidsforhold.fom shouldBe LocalDate.of(2022, 9, 5)
        inntektFraNyttArbeidsforhold.tom shouldBe basisdato
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer shouldBe "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer shouldBe "11224455441"
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

        val nyttArbeidsforholdSpm = soknaden.getSporsmalMedTag(NYTT_ARBEIDSFORHOLD_UNDERVEIS + "0")
        nyttArbeidsforholdSpm.sporsmalstekst!!.shouldContain("Har du jobbet noe hos Kiosken, avd Oslo AS i perioden")
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").asString() shouldBe "999888777"
        nyttArbeidsforholdSpm.metadata.get("arbeidsstedNavn").asString() shouldBe "Kiosken, avd Oslo AS"

        soknaden.sporsmal?.map { it.tag } shouldBe
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
        inntektFraNyttArbeidsforhold.fom shouldBe basisdato.plusDays(1)
        inntektFraNyttArbeidsforhold.tom shouldBe basisdato.plusDays(21)
        inntektFraNyttArbeidsforhold.belop shouldBe 4000
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer shouldBe "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer shouldBe "11224455441"
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
            .shouldBeEmpty()
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
            .shouldBeEmpty()
    }
}
