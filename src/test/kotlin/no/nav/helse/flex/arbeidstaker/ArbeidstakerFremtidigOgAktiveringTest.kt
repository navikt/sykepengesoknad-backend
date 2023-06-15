package no.nav.helse.flex.arbeidstaker

import no.nav.helse.flex.*
import no.nav.helse.flex.aktivering.AktiveringJob
import no.nav.helse.flex.client.medlemskap.MedlemskapVurderingRepository
import no.nav.helse.flex.client.medlemskap.MedlemskapVurderingResponse
import no.nav.helse.flex.client.medlemskap.MedlemskapVurderingSporsmal
import no.nav.helse.flex.client.medlemskap.MedlemskapVurderingSvarType
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstype
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.util.DatoUtil
import no.nav.helse.flex.util.serialisertTilString
import okhttp3.mockwebserver.MockResponse
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.shouldHaveSize
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class ArbeidstakerFremtidigOgAktiveringTest : BaseTestClass() {

    @Autowired
    private lateinit var aktiveringJob: AktiveringJob

    @Autowired
    private lateinit var medlemskapVurderingRepository: MedlemskapVurderingRepository

    private final val fnr = "123456789"
    private final val basisdato = LocalDate.now()

    @BeforeAll
    fun setup() {
        medlemskapMockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody(
                MedlemskapVurderingResponse(
                    svar = MedlemskapVurderingSvarType.UAVKLART,
                    sporsmal = listOf(
                        MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
                        MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
                        MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
                    )
                ).serialisertTilString()
            ).addHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        )
    }

    @Test
    @Order(1)
    fun `Fremtidig arbeidstakersøknad opprettes for en sykmelding`() {
        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(1),
                    tom = basisdato.plusDays(7)
                ) + heltSykmeldt(
                    fom = basisdato.plusDays(8),
                    tom = basisdato.plusDays(15)
                )
            )
        )
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.FREMTIDIG)

        val hentetViaRest = hentSoknaderMetadata(fnr)
        assertThat(hentetViaRest).hasSize(1)
        assertThat(hentetViaRest[0].soknadstype).isEqualTo(RSSoknadstype.ARBEIDSTAKERE)
        assertThat(hentetViaRest[0].status).isEqualTo(RSSoknadstatus.FREMTIDIG)
    }

    @Test
    @Order(2)
    fun `Søknaden har ingen spørsmål som fremtidig`() {
        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )
        assertThat(soknad.sporsmal!!).hasSize(0)
    }

    @Test
    @Order(3)
    fun `Vi aktiverer søknaden`() {
        aktiveringJob.bestillAktivering(now = basisdato.plusDays(16))
        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.NY)
    }

    @Test
    @Order(4)
    fun `Spørsmål om medlemskap fra LovMe er lagret i databasen`() {
        val medlemskapVurderingDbRecords = medlemskapVurderingRepository.findAll() shouldHaveSize 1
        val medlemskapVurdering = medlemskapVurderingDbRecords.first()

        medlemskapVurdering.fnr `should be equal to` fnr
        medlemskapVurdering.svartype `should be equal to` MedlemskapVurderingSvarType.UAVKLART.toString()
        medlemskapVurdering.sporsmal.value!! `should be equal to` listOf(
            MedlemskapVurderingSporsmal.OPPHOLDSTILATELSE,
            MedlemskapVurderingSporsmal.ARBEID_UTENFOR_NORGE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_EØS_OMRÅDE,
            MedlemskapVurderingSporsmal.OPPHOLD_UTENFOR_NORGE
        ).serialisertTilString()
    }

    @Test
    @Order(4)
    fun `Søknaden har forventa spørsmål som NY`() {
        val soknad = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first().id,
            fnr = fnr
        )

        assertThat(soknad.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                "ANSVARSERKLARING",
                "FRAVAR_FOR_SYKMELDINGEN",
                "TILBAKE_I_ARBEID",
                "FERIE_V2",
                "PERMISJON_V2",
                "UTLAND_V2",
                "ARBEID_UNDERVEIS_100_PROSENT_0",
                "ARBEID_UNDERVEIS_100_PROSENT_1",
                "ARBEID_UTENFOR_NORGE",
                "ANDRE_INNTEKTSKILDER_V2",
                "VAER_KLAR_OVER_AT",
                "BEKREFT_OPPLYSNINGER"
            )
        )

        assertThat(soknad.sporsmal!!.first { it.tag == "ARBEID_UNDERVEIS_100_PROSENT_0" }.sporsmalstekst).isEqualTo(
            "I perioden ${
            DatoUtil.formatterPeriode(
                basisdato.minusDays(1),
                basisdato.plusDays(7)
            )
            } var du 100 % sykmeldt fra Butikken. Jobbet du noe hos Butikken i denne perioden?"
        )

        assertThat(soknad.sporsmal!!.first { it.tag == "ARBEID_UNDERVEIS_100_PROSENT_1" }.sporsmalstekst).isEqualTo(
            "I perioden ${
            DatoUtil.formatterPeriode(
                basisdato.plusDays(8),
                basisdato.plusDays(15)
            )
            } var du 100 % sykmeldt fra Butikken. Jobbet du noe hos Butikken i denne perioden?"
        )
    }

    @Test
    @Order(5)
    fun `Vi besvarer og sender inn søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknad(
            soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
            fnr = fnr
        )

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "FRAVAR_FOR_SYKMELDINGEN", svar = "NEI")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_1", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
