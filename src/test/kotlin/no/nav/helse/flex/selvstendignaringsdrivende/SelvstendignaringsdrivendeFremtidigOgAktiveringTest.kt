package no.nav.helse.flex.selvstendignaringsdrivende

import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.client.grunnbeloep.GrunnbeloepResponse
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.mockdispatcher.grunnbeloep.GrunnbeloepApiMockDispatcher
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_OPPHOLD_I_UTLANDET
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_OPPRETTHOLDT_INNTEKT
import org.amshove.kluent.*
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.cache.RedisCacheManager
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SelvstendignaringsdrivendeFremtidigOgAktiveringTest : FellesTestOppsett() {
    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    lateinit var cacheManager: RedisCacheManager

    private final val fnr = "12345678901"
    private final val basisdato = LocalDate.parse("2025-01-01").plusYears(1000)

    @BeforeEach
    fun setup() {
        cacheManager.getCache("grunnbeloep-historikk")?.clear()
        GrunnbeloepApiMockDispatcher.clearQueue()
        flexSyketilfelleMockRestServiceServer.reset()
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_OPPHOLD_I_UTLANDET)
        fakeUnleash.enable(UNLEASH_CONTEXT_OPPRETTHOLDT_INNTEKT)
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
    }

    @Test
    @Order(1)
    fun `Selvstendig næringsdrivende søknad med status FREMTIDIG opprettes når vi mottar en sykmelding`() {
        GrunnbeloepApiMockDispatcher.enqueue(
            (basisdato.year - 5..basisdato.year).map { year ->
                GrunnbeloepResponse(
                    dato = "$year-05-01",
                    grunnbeløp = 118_620 + (year - 2024) * 10_000,
                    gjennomsnittPerÅr =
                        118_620 + (year - 2024) * 10_000,
                )
            },
        )

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
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.FREMTIDIG
        kafkaSoknader.first().arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest.shouldHaveSize(2)
        hentetViaRest[0].soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
        hentetViaRest[0].status `should be equal to` RSSoknadstatus.FREMTIDIG
        hentetViaRest[1].soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
        hentetViaRest[1].status `should be equal to` RSSoknadstatus.FREMTIDIG
    }

    @Test
    @Order(2)
    fun `Søknaden har ingen spørsmål`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        soknad.sporsmal.`should not be null`().shouldHaveSize(0)
    }

    @Test
    @Order(3)
    fun `Vi aktiverer søknaden`() {
        GrunnbeloepApiMockDispatcher.enqueue(
            (basisdato.year - 5..basisdato.year).map { year ->
                GrunnbeloepResponse(
                    dato = "$year-05-01",
                    grunnbeløp = 118_620 + (year - 2024) * 10_000,
                    gjennomsnittPerÅr =
                        118_620 + (year - 2024) * 10_000,
                )
            },
        )

        aktiveringJob.bestillAktivering(now = basisdato.plusDays(31))
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        kafkaSoknader.shouldHaveSize(1)
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.NY
        kafkaSoknader.first().arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE
    }

    @Test
    @Order(4)
    fun `Søknaden har forventa spørsmål som NY`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )

        soknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                FRAVAR_FOR_SYKMELDINGEN_V2,
                TILBAKE_I_ARBEID,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
                ANDRE_INNTEKTSKILDER,
                OPPHOLD_UTENFOR_EOS,
                NARINGSDRIVENDE_OPPHOLD_I_UTLANDET,
                INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
                TIL_SLUTT,
            )

        soknad.sporsmal.first { it.tag == "ARBEID_UNDERVEIS_100_PROSENT_0" }.sporsmalstekst!!.shouldContain(
            "1. - 31. januar 3025",
        )

        soknad.forstegangssoknad.`should not be null`().`should be true`()
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
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "NEI")
                .besvarSporsmal(tag = NARINGSDRIVENDE_OPPHOLD_I_UTLANDET, svar = "NEI")
                .besvarSporsmal(
                    tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
                    svar = null,
                    ferdigBesvart = false,
                ).besvarSporsmal(
                    tag = INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET_NEI,
                    svar = "CHECKED",
                    ferdigBesvart = false,
                ).besvarSporsmal(
                    tag = INNTEKTSOPPLYSNINGER_NY_I_ARBEIDSLIVET_NEI,
                    svar = "CHECKED",
                    ferdigBesvart = false,
                ).besvarSporsmal(tag = INNTEKTSOPPLYSNINGER_VARIG_ENDRING, svar = "NEI")
                .oppsummering()
                .sendSoknad()
        sendtSoknad.status `should be equal to` RSSoknadstatus.SENDT

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        kafkaSoknader.shouldHaveSize(1)
        kafkaSoknader.first().status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader.first().arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE
    }

    @Test
    @Order(6)
    fun `Søknad nr 2 er ikke førstegangssøknad, og inneholder riktige spørsmål`() {
        GrunnbeloepApiMockDispatcher.enqueue(
            (basisdato.year - 5..basisdato.year).map { year ->
                GrunnbeloepResponse(
                    dato = "$year-05-01",
                    grunnbeløp = 118_620 + (year - 2024) * 10_000,
                    gjennomsnittPerÅr =
                        118_620 + (year - 2024) * 10_000,
                )
            },
        )

        aktiveringJob.bestillAktivering(now = basisdato.plusDays(61))
        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        soknad.forstegangssoknad
            .`should not be null`()
            .`should be false`()

        soknad.sporsmal!!.map { it.tag } `should be equal to`
            listOf(
                ANSVARSERKLARING,
                TILBAKE_I_ARBEID,
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                NARINGSDRIVENDE_OPPRETTHOLDT_INNTEKT,
                ANDRE_INNTEKTSKILDER,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            )
    }
}
