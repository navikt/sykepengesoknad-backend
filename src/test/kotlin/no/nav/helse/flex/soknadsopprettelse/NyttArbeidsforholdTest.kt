package no.nav.helse.flex.soknadsopprettelse

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.`should be empty`
import org.amshove.kluent.`should be equal to`
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class NyttArbeidsforholdTest : FellesTestOppsett() {
    private val fnr = "22222220001"
    private final val basisdato = LocalDate.now().minusDays(1)

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
    @Order(2)
    fun `Har forventa tilkommen inntekt spm`() {
        val soknaden = hentSoknader(fnr = fnr).first()

        val tilkommenInntektSpm =
            soknaden.sporsmal!!.find {
                it.tag == "NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG"
            }!!
        tilkommenInntektSpm.sporsmalstekst `should be equal to` "Har du startet å jobbe hos Kiosken, avd Oslo AS?"
        tilkommenInntektSpm.metadata!!.get("orgnummer").textValue() `should be equal to` "999888777"
        tilkommenInntektSpm.metadata!!.get("orgnavn").textValue() `should be equal to` "Kiosken, avd Oslo AS"
    }

    @Test
    @Order(3)
    fun `Vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr = fnr).first()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
                .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
                .besvarSporsmal(tag = FERIE_V2, svar = "NEI")
                .besvarSporsmal(tag = PERMISJON_V2, svar = "NEI")
                .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
                .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG_FORSTE_ARBEIDSDAG,
                    svar = LocalDate.now().minusDays(4).toString(),
                    ferdigBesvart = false,
                )
                .besvarSporsmal(tag = NYTT_ARBEIDSFORHOLD_UNDERVEIS_BRUTTO, svar = "4000", ferdigBesvart = true)
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "NEI")
                .besvarSporsmal(tag = INNTEKTSKILDE_STYREVERV, svar = "CHECKED")
                .oppsummering()
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)

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

        hentSoknader(fnr = fnr).flatMap { it.sporsmal!! }.filter { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG }.`should be empty`()
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

        hentSoknader(fnr = fnr).flatMap { it.sporsmal!! }.filter { it.tag == NYTT_ARBEIDSFORHOLD_UNDERVEIS_FORSTEGANG }.`should be empty`()
    }
}
