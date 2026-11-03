@file:Suppress("ktlint:standard:max-line-length")

package no.nav.helse.flex.arbeidsledig

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSKilde
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.mockdispatcher.InntektskomponentenMockDispatcher
import no.nav.helse.flex.mockdispatcher.skapHentInntekterResponse
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.Kilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.KjentInntektskilde
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.KildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.KjenteInntektskilderDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.flatten
import no.nav.helse.flex.util.getSporsmalMedTag
import no.nav.helse.flex.util.serialisertTilString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class AndreInntektskilderSpmTest : FellesTestOppsett() {
    val ghostFnr = "11111234565"
    val kunEttArbeidsforholdFnr = "11111234566"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @Test
    fun `Har ingen registrerte arbeidsforhold og får kun spørsmål om andre inntekter`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                fnr = kunEttArbeidsforholdFnr,
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
        andreInntektskilderSpm.sporsmalstekst shouldBe
            "Har du annen inntekt eller oppdrag?"
        andreInntektskilderSpm.metadata!!.isNull shouldBe true

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = kunEttArbeidsforholdFnr)
                .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
                .besvarSporsmal(tag = FRISKMELDT, svar = "JA")
                .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
                .besvarSporsmal(tag = ARBEID_UTENFOR_NORGE, svar = "NEI")
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = INNTEKTSKILDE_STYREVERV, svar = "CHECKED")
                .oppsummering()
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].andreInntektskilder shouldBe
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
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato,
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
            it.navn shouldBe "Frilanseransetter AS"
            it.orgnummer shouldBe "999333667"
            it.kilde shouldBe RSKilde.INNTEKTSKOMPONENTEN
        }

        val andreInntektskilderSpm = soknaden.getSporsmalMedTag("FLERE_INNTEKTSKILDER_GHOST")
        andreInntektskilderSpm.sporsmalstekst shouldBe
            "Har du jobbet noe mer i disse enn du vanligvis gjør, mens du var sykmeldt i perioden 12. august - 1. september 2021?"

        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()

        val sendtSoknadMedGhost =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = ghostFnr)
                .besvarSporsmal(tag = ANSVARSERKLARING, svar = "CHECKED")
                .besvarSporsmal(tag = FRISKMELDT, svar = "JA")
                .besvarSporsmal(tag = OPPHOLD_UTENFOR_EOS, svar = "NEI")
                .besvarSporsmal(tag = ARBEID_UTENFOR_NORGE, svar = "NEI")
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
            .verdi shouldBe "JA"

        kafkaSoknaderMedGhost[0]
            .getSporsmalMedTag(medIndex(JOBBET_MER_I_VALG, 0))
            .also { sporsmal ->
                sporsmal.svar!!
                    .first()
                    .verdi shouldBe "CHECKED"

                sporsmal.sporsmalstekst shouldBe "Frilanseransetter AS"
            }

        kafkaSoknaderMedGhost[0].andreInntektskilder shouldBe
            listOf(
                InntektskildeDTO(
                    type = InntektskildetypeDTO.STYREVERV,
                    sykmeldt = null,
                ),
            )
    }

    @Test
    fun `V2 på toppnivå fjernes ved mutering av eldre søknad med ghost-inntekter`() {
        val fnr = "11111234568"
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
            ),
        )
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )
        soknaden.sporsmal!!.count { it.tag == ANDRE_INNTEKTSKILDER_V2 } shouldBe 1
        val lagretSoknad = sykepengesoknadRepository.findBySykepengesoknadUuid(soknaden.id)!!
        sykepengesoknadRepository.save(
            lagretSoknad.copy(
                ghostInntekter =
                    listOf(
                        KjentInntektskilde(
                            navn = "Frilanseransetter AS",
                            kilde = Kilde.INNTEKTSKOMPONENTEN,
                            orgnummer = "999333667",
                        ),
                    ).serialisertTilString(),
            ),
        )

        val mutert =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
                .besvarSporsmal(FRISKMELDT, "JA", mutert = true)

        mutert.rSSykepengesoknad.`har flere inntektskilder gruppe spørsmål`()
        hentSoknad(soknadId = soknaden.id, fnr = fnr).`har flere inntektskilder gruppe spørsmål`()
    }

    @Test
    fun `Ghost-spørsmål blir værende og dupliseres ikke ved friskmelding-mutering`() {
        val fnr = "11111234567"

        InntektskomponentenMockDispatcher.enqueue(
            skapHentInntekterResponse(
                fnr = fnr,
                loennsinntektOrgnumre = listOf("999333667"),
                frilansOrgnumre = listOf("999333667"),
            ),
        )

        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
                sykmeldingsperioder =
                    heltSykmeldt(
                        fom = basisdato.minusDays(20),
                        tom = basisdato,
                    ),
            ),
        )

        val soknaden =
            hentSoknad(
                soknadId =
                    hentSoknaderMetadata(fnr)
                        .first { it.status == RSSoknadstatus.NY }
                        .id,
                fnr = fnr,
            )

        val friskFraStart =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
                .besvarSporsmal(FRISKMELDT, "NEI", ferdigBesvart = false)
                .besvarSporsmal(FRISKMELDT_START, basisdato.minusDays(20).toString(), mutert = true)

        friskFraStart.rSSykepengesoknad.getSporsmalMedTagOrNull(FLERE_INNTEKTSKILDER_GRUPPE).shouldBeNull()
        friskFraStart.rSSykepengesoknad.getSporsmalMedTagOrNull(ANDRE_INNTEKTSKILDER_V2).shouldBeNull()

        val friskMidtI =
            friskFraStart
                .besvarSporsmal(FRISKMELDT_START, basisdato.minusDays(12).toString(), mutert = true)

        friskMidtI.rSSykepengesoknad.`har flere inntektskilder gruppe spørsmål`()
    }
}

private fun RSSykepengesoknad.`har flere inntektskilder gruppe spørsmål`() {
    this.sporsmal!!.count { it.tag == FLERE_INNTEKTSKILDER_GRUPPE } shouldBe 1
    this.sporsmal.none { it.tag == ANDRE_INNTEKTSKILDER_V2 } shouldBe true
    this.sporsmal
        .single { it.tag == FLERE_INNTEKTSKILDER_GRUPPE }
        .undersporsmal
        .map { it.tag } shouldBe
        listOf(FLERE_INNTEKTSKILDER_GHOST, ANDRE_INNTEKTSKILDER_V2)
}
