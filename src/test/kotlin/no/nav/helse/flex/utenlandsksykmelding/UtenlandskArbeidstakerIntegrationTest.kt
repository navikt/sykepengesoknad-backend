package no.nav.helse.flex.utenlandsksykmelding

import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknader
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.oppdaterSporsmalMedResult
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.testutil.byttSvar
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.util.flatten
import no.nav.helse.flex.ventPåRecords
import no.nav.syfo.model.sykmelding.arbeidsgiver.UtenlandskSykmeldingAGDTO
import org.amshove.kluent.`should be`
import org.amshove.kluent.`should be equal to`
import org.amshove.kluent.`should not be`
import org.amshove.kluent.shouldBeNull
import org.amshove.kluent.shouldBeTrue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.testcontainers.shaded.org.awaitility.Awaitility
import java.time.LocalDate
import java.util.concurrent.TimeUnit

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UtenlandskArbeidstakerIntegrationTest : FellesTestOppsett() {
    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private val fnr = "12345678900"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @Test
    @Order(1)
    fun `Arbeidstakersøknad opprettes`() {
        val kafkaSoknader =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder =
                        heltSykmeldt(
                            fom = basisdato,
                            tom = basisdato.plusDays(15),
                        ),
                    utenlandskSykemelding = UtenlandskSykmeldingAGDTO("hellas"),
                ),
            )

        kafkaSoknader[0].utenlandskSykmelding!!.shouldBeTrue()
    }

    @Test
    @Order(2)
    fun `Fritekst trenger svar når det er påkrevd`() {
        val soknaden = hentSoknader(fnr).first()
        val spm =
            soknaden.sporsmal!!.first { it.tag == "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE" }
                .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE", "JA")
        val response =
            oppdaterSporsmalMedResult(fnr, spm, soknaden.id).andExpect(status().isBadRequest)
                .andReturn().response.contentAsString
        response `should be equal to` "{\"reason\":\"SPORSMALETS_SVAR_VALIDERER_IKKE\"}"
    }

    @Test
    @Order(3)
    fun `Fritekst trenger lengde når det er påkrevd`() {
        val soknaden = hentSoknader(fnr).first()
        val spm =
            soknaden.sporsmal!!.first { it.tag == "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE" }
                .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE", "JA")
                .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE_FRITEKST", "")
        val response =
            oppdaterSporsmalMedResult(fnr, spm, soknaden.id).andExpect(status().isBadRequest)
                .andReturn().response.contentAsString
        response `should be equal to` "{\"reason\":\"SPORSMALETS_SVAR_VALIDERER_IKKE\"}"
    }

    @Test
    @Order(3)
    fun `Fritekst validerer maxlengde`() {
        val soknaden = hentSoknader(fnr).first()
        val spm =
            soknaden.sporsmal!!.first { it.tag == "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE" }
                .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE", "JA")
                .byttSvar(
                    "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE_FRITEKST",
                    "Veldig lang tekst Veldig lang tekstVeldig lang tekstVeldig lang tekstVeldig lang tekstVeldig " +
                        "lang tekstVeldig lang tekstVeldig lang tekstVeldig lang tekstVeldig lang tekst tekst tekstsdf " +
                        "sd ds sd der 200 ja ",
                )
        val response =
            oppdaterSporsmalMedResult(fnr, spm, soknaden.id).andExpect(status().isBadRequest)
                .andReturn().response.contentAsString
        response `should be equal to` "{\"reason\":\"SPORSMALETS_SVAR_VALIDERER_IKKE\"}"
    }

    @Test
    @Order(4)
    fun `Fritekst godtar min lengde `() {
        val soknaden = hentSoknader(fnr).first()
        val spm =
            soknaden.sporsmal!!.first { it.tag == "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE" }
                .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE", "JA")
                .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE_FRITEKST", "X")
        oppdaterSporsmalMedResult(fnr, spm, soknaden.id).andExpect(status().isOk).andReturn()
    }

    @Test
    @Order(6)
    fun `Vi besvarer og sender inn den første søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden =
            hentSoknad(
                soknadId = hentSoknaderMetadata(fnr).first { it.status == RSSoknadstatus.NY }.id,
                fnr = fnr,
            )

        val sendtSoknad =
            SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
                .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
                .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
                .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
                .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
                .besvarSporsmal(tag = "OPPHOLD_UTENFOR_EOS", svar = "NEI")
                .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_TRYGD_UTENFOR_NORGE", svar = "NEI")
                .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
                .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_BOSTED", svar = "JA", ferdigBesvart = false)
                .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_VEGNAVN", svar = "Downing Street", ferdigBesvart = false)
                .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_LAND", svar = "UK", ferdigBesvart = false)
                .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_TELEFONNUMMER", svar = "123456sdfgsdg", ferdigBesvart = false)
                .besvarSporsmal(
                    tag = "UTENLANDSK_SYKMELDING_GYLDIGHET_ADRESSE",
                    svar = soknaden.tom!!.plusDays(10).toString(),
                )
                .besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
                .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
                .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()
        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].utenlandskSykmelding!!.shouldBeTrue()
        kafkaSoknader[0].arbeidUtenforNorge.shouldBeNull()
        kafkaSoknader[0].sporsmal.flatten().first { it.tag == "UTENLANDSK_SYKMELDING_VEGNAVN" }.svar!!
            .first().verdi `should be equal to` "Downing Street"

        Awaitility.await().atMost(5, TimeUnit.SECONDS).untilAsserted {
            val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
            soknadFraDatabase.sendtArbeidsgiver `should not be` null
            soknadFraDatabase.sendtNav `should be` null
            soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver
        }
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }
}
