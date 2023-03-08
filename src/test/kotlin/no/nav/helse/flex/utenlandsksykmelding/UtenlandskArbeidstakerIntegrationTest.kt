package no.nav.helse.flex.utenlandsksykmelding

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.hentSoknad
import no.nav.helse.flex.hentSoknaderMetadata
import no.nav.helse.flex.mockFlexSyketilfelleArbeidsgiverperiode
import no.nav.helse.flex.repository.SykepengesoknadDAO
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.sykepengesoknad.kafka.PeriodeDTO
import no.nav.helse.flex.sykepengesoknad.kafka.SoknadsstatusDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import no.nav.helse.flex.tilSoknader
import no.nav.helse.flex.ventPåRecords
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
    fun `Arbeidstakersøknader opprettes for en lang sykmelding`() {
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
            .besvarSporsmal(tag = "FRAVAR_FOR_SYKMELDINGEN", svar = "JA", ferdigBesvart = false)
            .besvarSporsmal(
                tag = "FRAVAR_FOR_SYKMELDINGEN_NAR",
                svar = """{"fom":"${soknaden.fom!!.minusDays(14)}","tom":"${soknaden.fom!!.minusDays(7)}"}"""
            )
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UTENFOR_NORGE", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        val kafkaSoknader = sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        assertThat(kafkaSoknader).hasSize(1)
        assertThat(kafkaSoknader[0].status).isEqualTo(SoknadsstatusDTO.SENDT)
        kafkaSoknader[0].utenlandskSykmelding!!.shouldBeTrue()
        kafkaSoknader[0].arbeidUtenforNorge!!.`should be false`()
        assertThat(kafkaSoknader[0].fravarForSykmeldingen).isEqualTo(
            listOf(
                PeriodeDTO(
                    fom = soknaden.fom!!.minusDays(14),
                    tom = soknaden.fom!!.minusDays(7)
                )
            )
        )
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val soknadFraDatabase = sykepengesoknadDAO.finnSykepengesoknad(sendtSoknad.id)
        soknadFraDatabase.sendtArbeidsgiver `should not be` null
        soknadFraDatabase.sendtNav `should be` null
        soknadFraDatabase.sendt `should be equal to` soknadFraDatabase.sendtArbeidsgiver
    }
}
