@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.flatten
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_KJENTE_INNTEKTSKILDER
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should be null`
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KjenteOgAndreInntektskilderSpmTest : FellesTestOppsett() {
    private val fnr = "11111234565"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @Test
    @Order(1)
    fun `Arbeidstakersøknader opprettes for en lang sykmelding`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_KJENTE_INNTEKTSKILDER)
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
    @Order(3)
    fun `Har forventa kjente inntektskilder spm`() {
        val soknaden = hentSoknader(fnr).first()
        // Kun en undergruppe fordi frilanseren er filtrert bort
        soknaden.sporsmal!!.first { it.tag == KJENTE_INNTEKTSKILDER }.undersporsmal.shouldHaveSize(1)

        val spm = listOf(soknaden.sporsmal!!.first { it.tag == KJENTE_INNTEKTSKILDER }).flatten()
        val sporsmalstekster = spm.map { it.sporsmalstekst }

        sporsmalstekster[0] `should be equal to` "Du er oppført med flere inntektskilder i Arbeidsgiver- og arbeidstakerregisteret. Vi trenger mer informasjon om disse."
        sporsmalstekster[3] `should be equal to` "Har du sluttet hos Bensinstasjonen AS før du ble sykmeldt 12. august 2021?"
        sporsmalstekster[5] `should be equal to` "Når sluttet du?"
        sporsmalstekster[7] `should be equal to` "Har du utført noe arbeid ved Bensinstasjonen AS i perioden 28. juli - 11. august 2021?"

        // sjekker sorteringa
        val sporsmalstags = spm.map { it.tag }
        sporsmalstags[0] `should be equal to` KJENTE_INNTEKTSKILDER
        sporsmalstags[1] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_GRUPPE, 0)
        sporsmalstags[2] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_GRUPPE_TITTEL, 0)
        sporsmalstags[3] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_SLUTTET, 0)
        sporsmalstags[4] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_SLUTTET_JA, 0)
        sporsmalstags[5] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_DATO_SLUTTET, 0)
        sporsmalstags[6] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_SLUTTET_NEI, 0)
        sporsmalstags[7] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_UTFORT_ARBEID, 0)
        sporsmalstags[8] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET, 0)
        sporsmalstags[9] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_SYKMELDT, 0)
        sporsmalstags[10] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_TURNUS, 0)
        sporsmalstags[11] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_FERIE, 0)
        sporsmalstags[12] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_AVSPASERING, 0)
        sporsmalstags[13] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_PERMITTERT, 0)
        sporsmalstags[14] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_PERMISJON, 0)
        sporsmalstags[15] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_TILKALLINGSVIKAR, 0)
        sporsmalstags[16] `should be equal to` medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_ANNEN, 0)
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
                .besvarSporsmal(tag = UTLAND_V2, svar = "NEI")
                .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
                .besvarSporsmal(tag = medIndex(KJENTE_INNTEKTSKILDER_SLUTTET_NEI, 0), svar = "CHECKED", ferdigBesvart = false)
                .besvarSporsmal(tag = medIndex(KJENTE_INNTEKTSKILDER_UTFORT_ARBEID, 0), svar = "NEI", ferdigBesvart = false)
                .besvarSporsmal(tag = medIndex(KJENTE_INNTEKTSKILDER_ARSAK_IKKE_JOBBET_TURNUS, 0), svar = "CHECKED")
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSKILDE_STYREVERV, svar = "CHECKED")
                .besvarSporsmal(tag = TIL_SLUTT, svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
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

    @Test
    @Order(5)
    fun `Kjente inntektskilder lages ikke uten unleash toggle`() {
        databaseReset.resetDatabase()
        fakeUnleash.resetAll()
        fakeUnleash.disable(UNLEASH_CONTEXT_KJENTE_INNTEKTSKILDER)
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

        val soknaden = hentSoknader(fnr).first()
        soknaden.sporsmal?.firstOrNull { it.tag == KJENTE_INNTEKTSKILDER }.`should be null`()
    }
}
