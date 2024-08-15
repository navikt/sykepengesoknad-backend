@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AndreInntektskilderSpmTest : FellesTestOppsett() {
    private val fnr = "11111234565"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
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
    fun `Har forventa andre inntektskilder spm`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )
        soknaden.inntektskilderDataFraInntektskomponenten!!.shouldHaveSize(2)
        val arbeidstaker = soknaden.inntektskilderDataFraInntektskomponenten!!.first()
        arbeidstaker.navn `should be equal to` "Bensinstasjonen AS"
        arbeidstaker.orgnummer `should be equal to` "999333666"
        arbeidstaker.arbeidsforholdstype `should be equal to` Arbeidsforholdstype.ARBEIDSTAKER
        val frilanser = soknaden.inntektskilderDataFraInntektskomponenten!!.last()
        frilanser.navn `should be equal to` "Frilanseransetter AS"
        frilanser.orgnummer `should be equal to` "999333667"
        frilanser.arbeidsforholdstype `should be equal to` Arbeidsforholdstype.FRILANSER

        soknaden.sporsmal!!.find {
            it.tag == "ANDRE_INNTEKTSKILDER_V2"
        }!!.sporsmalstekst `should be equal to` "Har du andre inntektskilder enn Matbutikken AS, Bensinstasjonen AS og Frilanseransetter AS?"
    }

    @Test
    @Order(4)
    fun `Vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
                .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
                .besvarSporsmal(tag = FERIE_V2, svar = "NEI")
                .besvarSporsmal(tag = PERMISJON_V2, svar = "NEI")
                .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
                .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSKILDE_STYREVERV, svar = "CHECKED")
                .besvarSporsmal(tag = BEKREFT_OPPLYSNINGER, svar = "CHECKED")
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].andreInntektskilder `should be equal to`
            listOf(
                InntektskildeDTO(
                    type = InntektskildetypeDTO.STYREVERV,
                    sykmeldt = null,
                ),
            )

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
