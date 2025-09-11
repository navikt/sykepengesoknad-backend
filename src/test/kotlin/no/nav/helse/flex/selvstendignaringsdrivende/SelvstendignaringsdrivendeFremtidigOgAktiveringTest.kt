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
import no.nav.helse.flex.util.DatoUtil
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldContain
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class SelvstendignaringsdrivendeFremtidigOgAktiveringTest : FellesTestOppsett() {
    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    private final val fnr = "12345678901"
    private final val basisdato = LocalDate.now()

    @AfterAll
    fun hentAlleKafkaMeldinger() {
        juridiskVurderingKafkaConsumer.hentProduserteRecords()
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
                            fom = basisdato.minusDays(1),
                            tom = basisdato.plusDays(7),
                        ) +
                            heltSykmeldt(
                                fom = basisdato.plusDays(8),
                                tom = basisdato.plusDays(15),
                            ),
                ),
            )
        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.FREMTIDIG
        kafkaSoknader[0].arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE

        val hentetViaRest = hentSoknaderMetadata(fnr)
        hentetViaRest.shouldHaveSize(1)
        hentetViaRest[0].soknadstype `should be equal to` RSSoknadstype.SELVSTENDIGE_OG_FRILANSERE
        hentetViaRest[0].status `should be equal to` RSSoknadstatus.FREMTIDIG
    }

    @Test
    @Order(2)
    fun `Søknaden har ingen spørsmål`() {
        val soknad =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first().id,
                fnr = fnr,
            )
        soknad.sporsmal!!.shouldHaveSize(0)
    }

    @Test
    @Order(3)
    fun `Vi aktiverer søknaden`() {
        aktiveringJob.bestillAktivering(now = basisdato.plusDays(16))
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        kafkaSoknader.shouldHaveSize(1)
        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.NY
        kafkaSoknader[0].arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE
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
                "ARBEID_UNDERVEIS_100_PROSENT_1",
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                OPPHOLD_UTENFOR_EOS,
                INNTEKTSOPPLYSNINGER_VIRKSOMHETEN_AVVIKLET,
                TIL_SLUTT,
            )

        soknad.sporsmal.first { it.tag == "ARBEID_UNDERVEIS_100_PROSENT_0" }.sporsmalstekst!!.shouldContain(
            DatoUtil.formatterPeriode(
                basisdato.minusDays(1),
                basisdato.plusDays(7),
            ),
        )

        soknad.sporsmal.first { it.tag == "ARBEID_UNDERVEIS_100_PROSENT_1" }.sporsmalstekst!!.shouldContain(
            DatoUtil.formatterPeriode(
                basisdato.plusDays(8),
                basisdato.plusDays(15),
            ),
        )
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
                .besvarSporsmal(tag = ANDRE_INNTEKTSKILDER, svar = "NEI")
                .besvarSporsmal(tag = ARBEID_UTENFOR_NORGE, svar = "NEI")
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
        kafkaSoknader[0].status `should be equal to` SoknadsstatusDTO.SENDT
        kafkaSoknader[0].arbeidssituasjon `should be equal to` ArbeidssituasjonDTO.SELVSTENDIG_NARINGSDRIVENDE
    }
}
