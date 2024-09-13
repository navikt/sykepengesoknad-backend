package no.nav.helse.flex.nyttarbeidsforhold

import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.SoknadAktivering
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforholdNeiPåfølgendeTest : FellesTestOppsett() {
    private val fnr = "22222220001"
    private final val basisdato = LocalDate.now().minusDays(1)

    @Autowired
    lateinit var soknadAktivering: SoknadAktivering

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
        fakeUnleash.enable("sykepengesoknad-backend-tilkommen-inntekt")
    }

    @Test
    @Order(1)
    fun `Arbeidstakersøknader opprettes for en lang sykmelding`() {
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
    }


    @Test
    @Order(3)
    fun `Vi besvarer og sender inn søknade med ja på første`() {
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
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO, svar = "4000", ferdigBesvart = true)
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
        inntektFraNyttArbeidsforhold.belopPerDag `should be equal to` 800
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
    }

    @Test
    @Order(6)
    fun `Vi besvarer og sender inn den andre søknaden med nei på påfølgende`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first { it.status == RSSoknadstatus.NY }

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .standardSvar()
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_PAFOLGENDE, svar = "NEI")
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

         kafkaSoknader[0].inntektFraNyttArbeidsforhold!!.shouldBeEmpty()

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

}
