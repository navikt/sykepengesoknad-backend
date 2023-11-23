package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.flatten
import no.nav.helse.flex.soknadsopprettelse.Arbeidsforholdstype
import no.nav.helse.flex.soknadsopprettelse.KJENTE_INNTEKTSKILDER
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_KJENTE_INNTEKTSKILDER
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import no.nav.syfo.model.sykmeldingstatus.ArbeidsgiverStatusDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KjenteOgAndreInntektskilderSpmTest : BaseTestClass() {

    private val fnr = "11111234565"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @Test
    @Order(1)
    fun `Arbeidstakersøknader opprettes for en lang sykmelding`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_KJENTE_INNTEKTSKILDER)
        fakeUnleash.enable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(20),
                    tom = basisdato
                ),
                arbeidsgiver = ArbeidsgiverStatusDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS")
            )
        )
    }

    @Test
    @Order(2)
    fun `Har forventa andre inntektskilder spm`() {
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )
        soknaden.inntektskilderDataFraInntektskomponenten!!.shouldHaveSize(1)
        soknaden.inntektskilderDataFraInntektskomponenten!!.first().navn `should be equal to` "Bensinstasjonen AS"
        soknaden.inntektskilderDataFraInntektskomponenten!!.first().orgnummer `should be equal to` "999333666"
        soknaden.inntektskilderDataFraInntektskomponenten!!.first().arbeidsforholdstype `should be equal to` Arbeidsforholdstype.ARBEIDSTAKER

        soknaden.sporsmal!!.find { it.tag == "ANDRE_INNTEKTSKILDER_V2" }!!.sporsmalstekst `should be equal to` "Har du andre inntektskilder enn Matbutikken AS og Bensinstasjonen AS?"
    }

    @Test
    @Order(3)
    fun `Har forventa kjente inntektskilder spm`() {
        val soknaden = hentSoknader(fnr).first()
        val spm = soknaden.sporsmal!!.first { it.tag == KJENTE_INNTEKTSKILDER }
        val sporsmalstekster =
            listOf(spm).flatten().map { it.sporsmalstekst }
        sporsmalstekster[0] `should be equal to` "Du er oppført med flere inntektskilder i Arbeidsgiver- og arbeidstakerregisteret. Vi trenger mer informasjon om disse."
        sporsmalstekster[3] `should be equal to` "Jobber du fortsatt ved Bensinstasjonen AS?"
        sporsmalstekster[5] `should be equal to` "Har du utført arbeid ved Bensinstasjonen AS i minst én dag i perioden 28. juli - 11. august 2021?"
    }

    @Test
    @Order(4)
    fun `Vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "KJENTE_INNTEKTSKILDER_JOBBER_FORTSATT_JA_0", svar = "CHECKED", ferdigBesvart = false)
            .besvarSporsmal(tag = "KJENTE_INNTEKTSKILDER_UTFORT_ARBEID_0", svar = "NEI", ferdigBesvart = false)
            .besvarSporsmal(tag = "KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_TURNUS_0", svar = "CHECKED")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "INNTEKTSKILDE_STYREVERV", svar = "CHECKED")
            .besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].andreInntektskilder `should be equal to` listOf(
            InntektskildeDTO(
                type = InntektskildetypeDTO.STYREVERV,
                sykmeldt = null
            )
        )

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
