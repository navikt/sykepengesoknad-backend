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
import no.nav.helse.flex.util.getSporsmalMedTag
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.sykmelding.kafka.model.ArbeidsgiverStatusKafkaDTO
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AndreInntektskilderSpmTest : FellesTestOppsett() {
    val ghostFnr = "11111234565"
    val kunEttArbeidsforholdFnr = "11111234566"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.ventPåRecords(6)
    }

    @Test
    @Order(2)
    fun `Kun ett arbeidsforhold`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = kunEttArbeidsforholdFnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato,
                    ),
            ),
        )

        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(kunEttArbeidsforholdFnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = kunEttArbeidsforholdFnr,
            )
        soknaden.inntektskilderDataFraInntektskomponenten!!.shouldHaveSize(1)
        val frilanser = soknaden.inntektskilderDataFraInntektskomponenten.first()
        frilanser.navn `should be equal to` "Frilanseransetter AS"
        frilanser.orgnummer `should be equal to` "999333667"
        frilanser.arbeidsforholdstype `should be equal to` Arbeidsforholdstype.FRILANSER

        val andreInntektskilderSpm =
            soknaden.sporsmal!!.find {
                it.tag == "ANDRE_INNTEKTSKILDER_V2"
            }!!
        andreInntektskilderSpm.sporsmalstekst `should be equal to`
            "Har du annen inntekt eller oppdrag?"
        andreInntektskilderSpm.metadata!!.serialisertTilString() `should be equal to`
            """{"kjenteInntektskilder":[{"navn":"Butikken","kilde":"SYKMELDING","orgnummer":"123454543"},{"navn":"Frilanseransetter AS","kilde":"INNTEKTSKOMPONENTEN","orgnummer":"999333667"}]}""".trimMargin()
    }

    @Test
    @Order(2)
    fun `Har sporsmål om ghost inntekt`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = ghostFnr,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato,
                    ),
                arbeidsgiver = ArbeidsgiverStatusKafkaDTO(orgnummer = "123454543", orgNavn = "MATBUTIKKEN AS"),
            ),
        )

        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(ghostFnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = ghostFnr,
            )
        soknaden.inntektskilderDataFraInntektskomponenten!!.shouldHaveSize(2)
        val arbeidstaker = soknaden.inntektskilderDataFraInntektskomponenten.first()
        arbeidstaker.navn `should be equal to` "Bensinstasjonen AS"
        arbeidstaker.orgnummer `should be equal to` "999333666"
        arbeidstaker.arbeidsforholdstype `should be equal to` Arbeidsforholdstype.ARBEIDSTAKER
        val frilanser = soknaden.inntektskilderDataFraInntektskomponenten.last()
        frilanser.navn `should be equal to` "Frilanseransetter AS"
        frilanser.orgnummer `should be equal to` "999333667"
        frilanser.arbeidsforholdstype `should be equal to` Arbeidsforholdstype.FRILANSER

        val andreInntektskilderSpm = soknaden.getSporsmalMedTag("FLERE_INNTEKTSKILDER_GHOST")
        andreInntektskilderSpm.sporsmalstekst `should be equal to`
            "Har du jobbet noe mer i disse enn du vanligvis gjør, mens du var sykmeldt i perioden 12. august - 1. september 2021?"
        andreInntektskilderSpm.metadata!!.serialisertTilString() `should be equal to`
            """{"kjenteInntektskilder":[{"navn":"Matbutikken AS","kilde":"SYKMELDING","orgnummer":"123454543"},{"navn":"Bensinstasjonen AS","kilde":"INNTEKTSKOMPONENTEN","orgnummer":"999333666"},{"navn":"Frilanseransetter AS","kilde":"INNTEKTSKOMPONENTEN","orgnummer":"999333667"}]}"""
    }

    @Test
    @Order(4)
    fun `Besvarer og sender inn søknad med andre inntektskilder og en med ghost inntekt`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(kunEttArbeidsforholdFnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = kunEttArbeidsforholdFnr,
            )

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = kunEttArbeidsforholdFnr)
                .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
                .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
                .besvarSporsmal(tag = FERIE_V2, svar = "NEI")
                .besvarSporsmal(tag = PERMISJON_V2, svar = "NEI")
                .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
                .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSKILDE_STYREVERV, svar = "CHECKED")
                .oppsummering()
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

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknadMedGhost =
            hentSoknad(
                soknadId = hentSoknaderMetadata(ghostFnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = ghostFnr,
            )

        val sendtSoknadMedGhost =
            SoknadBesvarer(rSSykepengesoknad = soknadMedGhost, testOppsettInterfaces = this, fnr = ghostFnr)
                .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
                .besvarSporsmal(tag = TILBAKE_I_ARBEID, svar = "NEI")
                .besvarSporsmal(tag = FERIE_V2, svar = "NEI")
                .besvarSporsmal(tag = PERMISJON_V2, svar = "NEI")
                .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
                .besvarSporsmal(tag = medIndex(ARBEID_UNDERVEIS_100_PROSENT, 0), svar = "NEI")
                .besvarSporsmal(tag = FLERE_INNTEKTSKILDER_GHOST, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = medIndex(JOBBET_MER_I_VALG, 0), svar = "CHECKED", ferdigBesvart = false)
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSKILDE_STYREVERV, svar = "CHECKED")
                .oppsummering()
                .sendSoknad()
        assertThat(sendtSoknadMedGhost.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknaderMedGhost = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknaderMedGhost).hasSize(1)
        assertThat(kafkaSoknaderMedGhost[0].status).isEqualTo(SoknadsstatusDTO.SENDT)

        kafkaSoknaderMedGhost[0]
            .sporsmal!!
            .find { it.tag == FLERE_INNTEKTSKILDER_GHOST }!!
            .svar!!
            .first()
            .verdi `should be equal to` "JA"

        kafkaSoknaderMedGhost[0]
            .getSporsmalMedTag(medIndex(JOBBET_MER_I_VALG, 0))
            .also { sporsmal ->
                sporsmal.svar!!
                    .first()
                    .verdi `should be equal to` "CHECKED"

                sporsmal.sporsmalstekst `should be equal to` "Matbutikken AS"
            }

        kafkaSoknaderMedGhost[0].andreInntektskilder `should be equal to`
            listOf(
                InntektskildeDTO(
                    type = InntektskildetypeDTO.STYREVERV,
                    sykmeldt = null,
                ),
            )
    }
}
