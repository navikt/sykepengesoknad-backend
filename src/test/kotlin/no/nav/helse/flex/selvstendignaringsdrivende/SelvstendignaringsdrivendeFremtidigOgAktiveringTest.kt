package no.nav.helse.flex.selvstendignaringsdrivende

import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.sykepengesoknad.kafka.ArbeidssituasjonDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.unleash.UNLEASH_CONTEXT_OPPHOLD_I_UTLANDET
import org.amshove.kluent.*
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SelvstendignaringsdrivendeFremtidigOgAktiveringTest : FellesTestOppsett() {
    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    private final val fnr = "12345678901"
    private final val basisdato = LocalDate.parse("2025-01-01").plusYears(1000)

    @BeforeEach
    fun setup() {
        flexSyketilfelleMockRestServiceServer.reset()
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_OPPHOLD_I_UTLANDET)
    }

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.ventPåRecords(1)
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
                NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET,
                NARINGSDRIVENDE_VARIG_ENDRING,
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
                .besvarSporsmal(NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET, "JA", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = NARINGSDRIVENDE_VIRKSOMHETEN_AVVIKLET_DATO,
                    svar = soknaden.fom!!.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                    ferdigBesvart = true,
                    mutert = true,
                ).oppsummering()
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
