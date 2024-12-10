package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.mockdispatcher.AaregMockDispatcher
import no.nav.helse.flex.mockdispatcher.skapArbeidsforholdOversikt
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
class NyttArbeidsforholdKlippTest : FellesTestOppsett() {
    val fnr = "22222220001"

    @Test
    @Order(1)
    fun `første sykm opprettes for en lang sykmelding`() {
        fakeUnleash.resetAll()
        fakeUnleash.disable("sykepengesoknad-backend-tilkommen-inntekt")

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
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        kafkaSoknader.shouldHaveSize(1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(3)
    fun `Arbeidstakersøknader opprettes for en lang sykmelding`() {
        fakeUnleash.resetAll()
        fakeUnleash.disable("sykepengesoknad-backend-tilkommen-inntekt")

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
        val soknaden = hentSoknader(fnr = fnr).first()
        soknaden.sporsmal!!.find {
            it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS
        }.shouldBeNull()
    }

    @Test
    @Order(5)
    fun `Overlapp som ender etter`() {
        // skal ha denne Sykmelding yyy klipper søknad xxx tom fra: 2024-12-06 til: 2024-12-02

        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-tilkommen-inntekt")
        repeat(2) {
            AaregMockDispatcher.queuedArbeidsforholdOversikt.add(
                skapArbeidsforholdOversikt(
                    fnr = fnr,
                    startdato = LocalDate.of(2022, 8, 15),
                    sluttdato = LocalDate.of(2022, 8, 15),
                    arbeidssted = "999888777",
                    opplysningspliktigOrganisasjonsnummer = "123456789",
                ),
            )
        }

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = LocalDate.of(2022, 9, 13),
                        tom = LocalDate.of(2022, 9, 30),
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
            ),
            oppfolgingsdato = LocalDate.of(2022, 8, 1),
        )
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
    }

    @Test
    @Order(6)
    fun `Har nå forventa nytt arbeidsforhold førstegangsspørsmål`() {
        val soknader = hentSoknader(fnr = fnr).filter { it.status == RSSoknadstatus.NY }
        val eldsteSoknaden = soknader.sortedBy { it.fom }.first()
        eldsteSoknaden.fom `should be equal to` LocalDate.of(2022, 8, 26)
        eldsteSoknaden.tom `should be equal to` LocalDate.of(2022, 9, 12)
        val nyttArbeidsforholdSpm =
            eldsteSoknaden.sporsmal!!.find {
                it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS
            }!!
        @Suppress("ktlint:standard:max-line-length")
        nyttArbeidsforholdSpm.sporsmalstekst `should be equal to` "Har du jobbet noe hos Kiosken, avd Oslo AS i perioden 26. august - 12. september 2022?"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedOrgnummer").textValue() `should be equal to` "999888777"
        nyttArbeidsforholdSpm.metadata!!.get("arbeidsstedNavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
        nyttArbeidsforholdSpm.metadata!!.get("startdatoAareg").textValue() `should be equal to` "2022-08-15"
        nyttArbeidsforholdSpm.metadata!!.get("fom").textValue() `should be equal to` "2022-08-26"
        nyttArbeidsforholdSpm.metadata!!.get("tom").textValue() `should be equal to` "2022-09-12"
        nyttArbeidsforholdSpm.undersporsmal.map { it.tag } `should be equal to`
            listOf(
                NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO,
            )
        eldsteSoknaden.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                FERIE_V2,
                PERMISJON_V2,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                NYTT_ARBEIDSFORHOLD_UNDERVEIS,
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )
    }

    @Test
    @Order(8)
    fun `Vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).filter { it.status == RSSoknadstatus.NY }.sortedBy { it.fom }.first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS, svar = "NEI", ferdigBesvart = true)
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
        inntektFraNyttArbeidsforhold.fom `should be equal to` LocalDate.of(2022, 8, 26)
        inntektFraNyttArbeidsforhold.tom `should be equal to` LocalDate.of(2022, 9, 12)
        inntektFraNyttArbeidsforhold.arbeidsstedOrgnummer `should be equal to` "999888777"
        inntektFraNyttArbeidsforhold.opplysningspliktigOrgnummer `should be equal to` "123456789"

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
