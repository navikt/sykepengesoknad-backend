@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.behandlingsdager

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSKilde
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.mockdispatcher.InntektskomponentenMockDispatcher
import no.nav.helse.flex.mockdispatcher.skapHentInntekterResponse
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.KildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.KjenteInntektskilderDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.behandlingsdager
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.flatten
import no.nav.helse.flex.util.getSporsmalMedTag
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldBeEmpty
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AndreInntektskilderSpmTest : FellesTestOppsett() {
    val ghostFnr = "11111234565"
    val kunEttArbeidsforholdFnr = "11111234566"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.ventPåRecords(4)
    }

    @Test
    fun `Har ingen registrerte arbeidsforhold og får kun spørsmål om andre inntekter`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = kunEttArbeidsforholdFnr,
                sykmeldingsperioder =
                    behandlingsdager(
                        fom = basisdato,
                        tom = basisdato.plusDays(2),
                        behandlingsdager = 1,
                    ),
            ),
        )

        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(kunEttArbeidsforholdFnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = kunEttArbeidsforholdFnr,
            )
        soknaden.inntektskilderDataFraInntektskomponenten!!.shouldBeEmpty()
        soknaden.ghostInntekter!!.shouldBeEmpty()

        val andreInntektskilderSpm = soknaden.getSporsmalMedTag("ANDRE_INNTEKTSKILDER_V2")
        andreInntektskilderSpm.sporsmalstekst `should be equal to`
            "Har du annen inntekt eller oppdrag?"
        andreInntektskilderSpm.metadata!!.isNull `should be equal to` true

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = kunEttArbeidsforholdFnr)
                .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
                .besvarSporsmal(
                    tag = medIndex(ENKELTSTAENDE_BEHANDLINGSDAGER_UKE, 0),
                    svar = "${soknaden.fom}",
                ).besvarSporsmal(tag = ARBEID_UTENFOR_NORGE, svar = "NEI")
                .besvarSporsmal(tag = FERIE_V2, svar = "NEI")
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
    }

    @Test
    fun `Får ghost-spørsmål fra inntektskomponenten og andre inntektskilder`() {
        InntektskomponentenMockDispatcher.enqueue(
            skapHentInntekterResponse(
                fnr = ghostFnr,
                loennsinntektOrgnumre = listOf("999333667"),
                frilansOrgnumre = listOf("999333667"),
            ),
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = ghostFnr,
                sykmeldingsperioder =
                    behandlingsdager(
                        fom = basisdato,
                        tom = basisdato.plusDays(2),
                        behandlingsdager = 1,
                    ),
            ),
        )

        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(ghostFnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = ghostFnr,
            )

        soknaden.ghostInntekter!!.shouldHaveSize(1)
        soknaden.ghostInntekter[0].let {
            it.navn `should be equal to` "Frilanseransetter AS"
            it.orgnummer `should be equal to` "999333667"
            it.kilde `should be equal to` RSKilde.INNTEKTSKOMPONENTEN
        }

        val flereInntektskilderSpm = soknaden.getSporsmalMedTag("FLERE_INNTEKTSKILDER_GHOST")
        flereInntektskilderSpm.sporsmalstekst `should be equal to`
            "Har du jobbet noe mer i disse enn du vanligvis gjør, mens du var sykmeldt i perioden 1. - 3. september 2021?"

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val sendtSoknadMedGhost =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = ghostFnr)
                .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
                .besvarSporsmal(
                    tag = medIndex(ENKELTSTAENDE_BEHANDLINGSDAGER_UKE, 0),
                    svar = "${soknaden.fom}",
                ).besvarSporsmal(tag = ARBEID_UTENFOR_NORGE, svar = "NEI")
                .besvarSporsmal(tag = FERIE_V2, svar = "NEI")
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
        assertThat(kafkaSoknaderMedGhost[0].flereInntektskilderGhost)
            .containsExactlyInAnyOrder(
                KjenteInntektskilderDTO("Frilanseransetter AS", KildeDTO.INNTEKTSKOMPONENTEN, "999333667"),
            )

        kafkaSoknaderMedGhost[0]
            .sporsmal!!
            .flatten()
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

                sporsmal.sporsmalstekst `should be equal to` "Frilanseransetter AS"
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
