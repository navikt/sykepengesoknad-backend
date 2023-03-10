package no.nav.helse.flex.utenlandsksykmelding

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.testutil.byttSvar
import no.nav.helse.flex.util.flatten
import no.nav.syfo.model.sykmelding.arbeidsgiver.UtenlandskSykmeldingAGDTO
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.LocalDate

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class UtenlandskArbeidstakerIntegrationTest : BaseTestClass() {

    @Autowired
    private lateinit var sykepengesoknadDAO: SykepengesoknadDAO

    private final val fnr = "12454578474"
    private final val basisdato = LocalDate.of(2021, 9, 1)

    @Test
    @Order(1)
    fun `Arbeidstakersøknad opprettes`() {
        val kafkaSoknader = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato,
                    tom = basisdato.plusDays(15)

                ),
                utenlandskSykemelding = UtenlandskSykmeldingAGDTO("hellas")
            )
        )

        kafkaSoknader[0].utenlandskSykmelding!!.shouldBeTrue()
    }

    @Test
    @Order(2)
    fun `Fritekst trenger svar når det er påkrevd`() {
        val soknaden = hentSoknader(fnr).first()
        val spm = soknaden.sporsmal!!.first { it.tag == "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE" }
            .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE", "JA")
        val response = oppdaterSporsmalMedResult(fnr, spm, soknaden.id).andExpect(status().isBadRequest)
            .andReturn().response.contentAsString
        response `should be equal to` "{\"reason\":\"SPORSMALETS_SVAR_VALIDERER_IKKE\"}"
    }

    @Test
    @Order(3)
    fun `Fritekst trenger lengde når det er påkrevd`() {
        val soknaden = hentSoknader(fnr).first()
        val spm = soknaden.sporsmal!!.first { it.tag == "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE" }
            .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE", "JA")
            .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE_FRITEKST", "")
        val response = oppdaterSporsmalMedResult(fnr, spm, soknaden.id).andExpect(status().isBadRequest)
            .andReturn().response.contentAsString
        response `should be equal to` "{\"reason\":\"SPORSMALETS_SVAR_VALIDERER_IKKE\"}"
    }

    @Test
    @Order(4)
    fun `Fritekst godtar min lengde `() {
        val soknaden = hentSoknader(fnr).first()
        val spm = soknaden.sporsmal!!.first { it.tag == "UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE" }
            .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE", "JA")
            .byttSvar("UTENLANDSK_SYKMELDING_LONNET_ARBEID_UTENFOR_NORGE_FRITEKST", "X")
        oppdaterSporsmalMedResult(fnr, spm, soknaden.id).andExpect(status().isOk).andReturn()
    }

    @Test
    @Order(6)
    fun `Vi besvarer og sender inn den første søknaden`() {
        flexSyketilfelleMockRestServiceServer?.reset()
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
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_TRYGD_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_BOSTED", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_VEGNAVN", svar = "Downing Street", ferdigBesvart = false)
            .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_LAND", svar = "UK", ferdigBesvart = false)
            .besvarSporsmal(tag = "UTENLANDSK_SYKMELDING_TELEFONNUMMER", svar = "123456sdfgsdg", ferdigBesvart = false)
            .besvarSporsmal(
                tag = "UTENLANDSK_SYKMELDING_GYLDIGHET_ADRESSE",
                svar = """{"fom":"${soknaden.fom!!.minusDays(14)}","tom":"${soknaden.fom!!.minusDays(7)}"}"""
            )
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].utenlandskSykmelding!!.shouldBeTrue()
        kafkaSoknader[0].arbeidUtenforNorge!!.`should be false`()
        kafkaSoknader[0].sporsmal.flatten().first { it.tag == "UTENLANDSK_SYKMELDING_VEGNAVN" }.svar!!
            .first().verdi `should be equal to` "Downing Street"

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        soknadFraDatabase.sendtArbeidsgiver `should not be` null
        soknadFraDatabase.sendtNav `should be` null
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver
    }
}
