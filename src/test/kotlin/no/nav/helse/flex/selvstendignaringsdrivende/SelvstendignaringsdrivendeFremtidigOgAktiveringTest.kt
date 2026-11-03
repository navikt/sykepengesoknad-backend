package no.nav.helse.flex.selvstendignaringsdrivende

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.client.inntektskomponenten.HentInntekterResponse
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSKilde
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.mockdispatcher.InntektskomponentenMockDispatcher
import no.nav.helse.flex.mockdispatcher.skapHentInntekterResponse
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.soknadsopprettelse.sporsmal.medlemskap.medIndex
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.InntektskildetypeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.KildeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.KjenteInntektskilderDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.getSporsmalMedTag
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SelvstendignaringsdrivendeFremtidigOgAktiveringTest : FellesTestOppsett() {
    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private final val fnr = "12345678901"
    private final val basisdato = LocalDate.parse("2025-01-01").plusYears(1000)

    @BeforeEach
    fun setup() {
        flexSyketilfelleMockRestServiceServer.reset()
    }

    @Test
    @Order(1)
    fun `Selvstendig næringsdrivende søknad med status FREMTIDIG opprettes når vi mottar en sykmelding`() {
        val kafkaSoknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato,
                            tom = basisdato.plusDays(30),
                        ) +
                            heltSykmeldt(
                                fom = basisdato.plusDays(31),
                                tom = basisdato.plusDays(60),
                            ),
                ),
                forventaSoknader = 2,
            )
        kafkaSoknader.first().status shouldBe SoknadsstatusDTO.FREMTIDIG
        kafkaSoknader.first().arbeidssituasjon shouldBe ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest.shouldHaveSize(2)
        hentetViaRest[0].soknadstype shouldBe RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
        hentetViaRest[0].status shouldBe RSSoknadstatus.FREMTIDIG
        hentetViaRest[1].soknadstype shouldBe RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
        hentetViaRest[1].status shouldBe RSSoknadstatus.FREMTIDIG
    }

    @Test
    @Order(2)
    fun `Søknaden har ingen spørsmål`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        soknad.sporsmal.shouldNotBeNull().shouldHaveSize(0)
    }

    @Test
    @Order(3)
    fun `Vi aktiverer søknaden`() {
        aktiveringJob.bestillAktivering(now = basisdato.plusDays(31))
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        kafkaSoknader.shouldHaveSize(1)
        kafkaSoknader.first().status shouldBe SoknadsstatusDTO.NY
        kafkaSoknader.first().arbeidssituasjon shouldBe ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE
    }

    @Test
    @Order(4)
    fun `Søknaden har forventa spørsmål som NY`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        soknad.sporsmal!!.map { it.tag } shouldBe
            listOf(
                ANSVARSERKLARING,
                FRAVAR_FOR_SYKMELDINGEN_V2,
                TILBAKE_I_ARBEID,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                NARINGSDRIVENDE_VARIG_ENDRING,
                TIL_SLUTT,
            )

        soknad.getSporsmalMedTag("ARBEID_UNDERVEIS_100_PROSENT_0").sporsmalstekst!!.shouldContain(
            "1. - 31. januar 3025",
        )

        soknad.forstegangssoknad.shouldNotBeNull().shouldBeTrue()
    }

    @Test
    @Order(5)
    fun `Vi besvarer og sender inn søknaden`() {
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, testOppsettInterfaces = this, fnr = fnr)
                .standardSvar(
                    ekskludert =
                        listOf(
                            ANDRE_INNTEKTSKILDER_V2,
                            FERIE_V2,
                            PERMISJON_V2,
                        ),
                ).besvarSporsmal(tag = FRAVAR_FOR_SYKMELDINGEN_V2, svar = "NEI")
                .besvarSporsmal(tag = NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT, svar = "NEI")
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER_V2, svar = "NEI")
                .besvarSporsmal(tag = NARINGSDRIVENDE_OPPHOLD_I_UTLANDET, svar = "NEI")
                .besvarSporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "JA", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET_DATO,
                    svar = soknaden.fom!!.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                    ferdigBesvart = true,
                    mutert = true,
                ).oppsummering()
                .sendSoknad()
        sendtSoknad.status shouldBe RSSoknadstatus.SENDT

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        kafkaSoknader.shouldHaveSize(1)
        kafkaSoknader.first().status shouldBe SoknadsstatusDTO.SENDT
        kafkaSoknader.first().arbeidssituasjon shouldBe ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE

        juridiskVurderingKafkaConsumer.ventPåRecords(1)
    }

    @Test
    @Order(6)
    fun `Søknad nr 2 er ikke førstegangssøknad, og inneholder riktige spørsmål`() {
        aktiveringJob.bestillAktivering(now = basisdato.plusDays(61))
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        soknad.forstegangssoknad
            .shouldNotBeNull()
            .shouldBeFalse()

        soknad.sporsmal!!.map { it.tag } shouldBe
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
                ANDRE_INNTEKTSKILDER_V2,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )
    }

    @Nested
    inner class GhostSporsmal {
        @Test
        fun `Søknad skal få spørsmål om ghost ettersom det er flere ghostinntekter`() {
            val ghostFnr = "1234567654"
            val soknad = opprettSoknadMedGhostSporsmal(ghostFnr)

            soknad.sporsmal!!.map { it.tag } shouldContain FLERE_INNTEKTSKILDER_GRUPPE
            soknad.sporsmal.map { it.tag } shouldNotContain ANDRE_INNTEKTSKILDER_V2
            soknad.getSporsmalMedTag(FLERE_INNTEKTSKILDER_GHOST).sporsmalstekst shouldBe
                "Har du jobbet noe mer i disse enn du vanligvis gjør, mens du var sykmeldt i perioden 1. - 21. januar 3025?"
            soknad.getSporsmalMedTag(medIndex(JOBBET_MER_I_VALG, 0)).sporsmalstekst shouldBe "Matbutikken AS"
            soknad.inntektskilderDataFraInntektskomponenten.shouldNotBeNull().map { it.orgnummer } shouldBe
                listOf("123454543")
            soknad.ghostInntekter.shouldNotBeNull().map { it.orgnummer } shouldBe listOf("123454543")

            val lagretSoknad = sykepengesoknadDAO.finnSykepengesoknad(soknad.id)
            lagretSoknad.ghostInntekter.shouldNotBeNull().map { it.orgnummer } shouldBe listOf("123454543")
            lagretSoknad.arbeidsforholdFraAareg.shouldNotBeNull().shouldBeEmpty()
        }

        @Test
        fun `Frilansinntekt fra inntektskomponenten blir ghost-inntekt`() {
            val ghostFnr = "1234567656"
            val soknad =
                opprettSoknadMedGhostSporsmal(
                    fnr = ghostFnr,
                    inntekter =
                        skapHentInntekterResponse(
                            fnr = ghostFnr,
                            loennsinntektOrgnumre = listOf("999333667"),
                            frilansOrgnumre = listOf("999333667"),
                        ),
                    forventetGhostOrgnummer = "999333667",
                )

            soknad.inntektskilderDataFraInntektskomponenten
                .shouldNotBeNull()
                .single()
                .arbeidsforholdstype shouldBe Arbeidsforholdstype.FRILANSER
            soknad.getSporsmalMedTag(medIndex(JOBBET_MER_I_VALG, 0)).sporsmalstekst shouldBe "Frilanseransetter AS"
        }

        @Test
        fun `Arbeidsforhold fra inntektskomponenten vises i ghost-spørsmålet`() {
            val ghostFnr = "1234567657"
            val soknad =
                opprettSoknadMedGhostSporsmal(
                    fnr = ghostFnr,
                    inntekter = skapHentInntekterResponse(fnr = ghostFnr, loennsinntektOrgnumre = listOf("999333666")),
                    forventetGhostOrgnummer = "999333666",
                )

            soknad.sporsmal!!
                .map { it.tag }
                .none { it.startsWith(NYTT_ARBEIDSFORHOLD_UNDERVEIS) }
                .shouldBeTrue()
            soknad.getSporsmalMedTag(medIndex(JOBBET_MER_I_VALG, 0)).sporsmalstekst shouldBe "Bensinstasjonen AS"
            soknad.ghostInntekter
                .shouldNotBeNull()
                .single()
                .kilde shouldBe
                RSKilde.INNTEKTSKOMPONENTEN
            sykepengesoknadDAO
                .finnSykepengesoknad(soknad.id)
                .arbeidsforholdFraAareg
                .shouldNotBeNull()
                .shouldBeEmpty()
        }

        @Test
        fun `Uten inntekt fra inntektskomponenten får søknaden kun spørsmål om andre inntektskilder`() {
            val ghostFnr = "1234567660"
            InntektskomponentenMockDispatcher.enqueue(skapHentInntekterResponse(fnr = ghostFnr))
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = ghostFnr,
                    sykmeldingsperioder = heltSykmeldt(fom = basisdato, tom = basisdato.plusDays(20)),
                ),
            )
            aktiveringJob.bestillAktivering(now = basisdato.plusDays(61))
            val aktivertSoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().single()
            aktivertSoknad.flereInntektskilderGhost.shouldNotBeNull().shouldBeEmpty()
            val soknad =
                hentSoknad(
                    soknadId = aktivertSoknad.id,
                    fnr = ghostFnr,
                )

            soknad.inntektskilderDataFraInntektskomponenten.shouldNotBeNull().shouldBeEmpty()
            soknad.sporsmal!!.map { it.tag } shouldContain ANDRE_INNTEKTSKILDER_V2
            soknad.sporsmal.map { it.tag } shouldNotContain FLERE_INNTEKTSKILDER_GRUPPE
            sykepengesoknadDAO
                .finnSykepengesoknad(soknad.id)
                .ghostInntekter
                .shouldNotBeNull()
                .shouldBeEmpty()
        }

        @Test
        fun `Det går fint å svare på ghost-spørsmålet`() {
            val ghostFnr = "1234567655"
            val soknad = opprettSoknadMedGhostSporsmal(ghostFnr)

            besvarGhostSporsmal(soknad, ghostFnr)

            val besvartSoknad = hentSoknad(soknadId = soknad.id, fnr = ghostFnr)
            forventedeGhostSvar.forEach { (tag, forventetSvar) ->
                besvartSoknad.getSporsmalMedTag(tag).svar.map { it.verdi } shouldBe listOf(forventetSvar)
            }
        }

        @Test
        fun `Sendt søknad med ghost-svar publiseres på Kafka med ghost-inntekter og andre inntektskilder`() {
            val ghostFnr = "1234567658"
            val soknad = opprettSoknadMedGhostSporsmal(ghostFnr)

            val sendtSoknad =
                besvarGhostSporsmal(soknad, ghostFnr)
                    .standardSvar(ekskludert = listOf(ANDRE_INNTEKTSKILDER_V2))
                    .besvarSporsmal(tag = FRAVAR_FOR_SYKMELDINGEN_V2, svar = "NEI")
                    .besvarSporsmal(tag = NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT, svar = "NEI")
                    .besvarSporsmal(tag = NARINGSDRIVENDE_OPPHOLD_I_UTLANDET, svar = "NEI")
                    .besvarSporsmal(tag = NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, svar = "NEI")
                    .besvarSporsmal(tag = NARINGSDRIVENDE_VARIG_ENDRING, svar = "NEI")
                    .oppsummering()
                    .sendSoknad()
            sendtSoknad.status shouldBe RSSoknadstatus.SENDT

            val kafkaSoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().single()
            kafkaSoknad.status shouldBe SoknadsstatusDTO.SENDT
            kafkaSoknad.selvstendigNaringsdrivende!!.hovedSporsmalSvar.let {
                it[FLERE_INNTEKTSKILDER_GHOST] shouldBe true
                it[ANDRE_INNTEKTSKILDER_V2] shouldBe true
            }
            kafkaSoknad.flereInntektskilderGhost shouldBe
                setOf(KjenteInntektskilderDTO("Matbutikken AS", KildeDTO.INNTEKTSKOMPONENTEN, "123454543"))
            forventedeGhostSvar.forEach { (tag, forventetSvar) ->
                kafkaSoknad.getSporsmalMedTag(tag).svar!!.map { it.verdi } shouldBe listOf(forventetSvar)
            }
            kafkaSoknad.andreInntektskilder shouldBe
                listOf(InntektskildeDTO(type = InntektskildetypeDTO.STYREVERV, sykmeldt = null))

            juridiskVurderingKafkaConsumer.ventPåRecords(1)
        }

        private val forventedeGhostSvar =
            listOf(
                FLERE_INNTEKTSKILDER_GHOST to "JA",
                medIndex(JOBBET_MER_I_VALG, 0) to "CHECKED",
                ANDRE_INNTEKTSKILDER_V2 to "JA",
                INNTEKTSKILDE_STYREVERV to "CHECKED",
            )

        private fun besvarGhostSporsmal(
            soknad: RSSykepengesoknad,
            fnr: String,
        ): SoknadBesvarer =
            SoknadBesvarer(
                rSSykepengesoknad = soknad,
                testOppsettInterfaces = this@SelvstendignaringsdrivendeFremtidigOgAktiveringTest,
                fnr = fnr,
            ).besvarSporsmal(
                tag = FLERE_INNTEKTSKILDER_GHOST,
                svar = "JA",
                ferdigBesvart = false,
            ).besvarSporsmal(
                tag = medIndex(JOBBET_MER_I_VALG, 0),
                svar = "CHECKED",
                ferdigBesvart = false,
            ).besvarSporsmal(
                tag = ANDRE_INNTEKTSKILDER_V2,
                svar = "JA",
                ferdigBesvart = false,
            ).besvarSporsmal(tag = INNTEKTSKILDE_STYREVERV, svar = "CHECKED")

        private fun opprettSoknadMedGhostSporsmal(
            fnr: String,
            inntekter: HentInntekterResponse = skapHentInntekterResponse(fnr = fnr, loennsinntektOrgnumre = listOf("123454543")),
            forventetGhostOrgnummer: String = "123454543",
        ): RSSykepengesoknad {
            InntektskomponentenMockDispatcher.enqueue(inntekter)
            sendSykmelding(
                sykmeldingKafkaMessage(
                    arbeidssituasjon = Arbeidssituasjon.NAERINGSDRIVENDE,
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(fom = basisdato, tom = basisdato.plusDays(20)),
                ),
            )

            aktiveringJob.bestillAktivering(now = basisdato.plusDays(61))
            val aktivertSoknad = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader().single()
            aktivertSoknad.flereInntektskilderGhost.shouldNotBeNull().map { it.orgnummer } shouldBe
                listOf(forventetGhostOrgnummer)

            return hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )
        }
    }
}
