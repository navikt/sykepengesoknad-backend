package no.nav.helse.flex.korrigering

import no.nav.helse.flex.*
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSoknadstatus
import no.nav.helse.flex.soknadsopprettelse.sporsmal.UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.testutil.SoknadBesvarer
import org.amshove.kluent.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
class KorrigeringTest : BaseTestClass() {

    private val fnr = "12345678900"
    private val basisdato = LocalDate.now()

    @Test
    @Order(1)
    fun `Arbeidstakersøknad opprettes`() {
        fakeUnleash.resetAll()
        fakeUnleash.enable(UNLEASH_CONTEXT_TIL_SLUTT_SPORSMAL)
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt(
                    fom = basisdato.minusDays(20),
                    tom = basisdato.minusDays(1)

                )
            )
        )

        val soknader = hentSoknader(fnr)
        soknader.first().korrigeringsfristUtlopt.shouldBeNull()
    }

    @Test
    @Order(2)
    fun `Vi kan ikke korrigere en soknad som ikke er sendt`() {
        val soknaden = hentSoknaderMetadata(fnr)[0]
        korrigerSoknadMedResult(soknaden.id, fnr).andExpect(status().isBadRequest)
            .andReturn()
    }

    @Test
    @Order(3)
    fun `Vi besvarer og sender inn den første søknaden`() {
        flexSyketilfelleMockRestServiceServer.reset()
        mockFlexSyketilfelleArbeidsgiverperiode()
        val soknaden = hentSoknader(fnr).first()

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = soknaden, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TILBAKE_I_ARBEID", svar = "NEI")
            .besvarSporsmal(tag = "FERIE_V2", svar = "NEI")
            .besvarSporsmal(tag = "PERMISJON_V2", svar = "NEI")
            .besvarSporsmal(tag = "UTLAND_V2", svar = "NEI")
            .besvarSporsmal(tag = "ARBEID_UNDERVEIS_100_PROSENT_0", svar = "NEI")
            .besvarSporsmal(tag = "ANDRE_INNTEKTSKILDER_V2", svar = "NEI")
            .besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg lover å ikke lyve!", ferdigBesvart = false)
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1).tilSoknader()

        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)

        val soknadenEtter = hentSoknader(fnr).first()
        soknadenEtter.korrigeringsfristUtlopt `should be` false
    }

    @Test
    @Order(4)
    fun `Korrigeringsfristen blir utløpt hvis vi setter sendt 13 mnd tilbake`() {
        val soknaden = hentSoknader(fnr).first()
        sykepengesoknadRepository.findBySykepengesoknadUuid(soknaden.id)?.let {
            sykepengesoknadRepository.save(it.copy(sendt = OffsetDateTime.now().minusMonths(13).toInstant()))
        }

        val soknadenEtter = hentSoknader(fnr).first()
        soknadenEtter.korrigeringsfristUtlopt `should be` true

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknaden.id)?.let {
            sykepengesoknadRepository.save(it.copy(sendt = Instant.now()))
        }
    }

    @Test
    @Order(5)
    fun `Vi korrigerer og sender inn`() {
        val soknaden = hentSoknader(fnr).first()

        val korrigerendeSoknad = korrigerSoknad(soknaden.id, fnr)
        mockFlexSyketilfelleArbeidsgiverperiode(andreKorrigerteRessurser = soknaden.id)

        val sendtSoknad = SoknadBesvarer(rSSykepengesoknad = korrigerendeSoknad, mockMvc = this, fnr = fnr)
            .besvarSporsmal(tag = "ANSVARSERKLARING", svar = "CHECKED")
            .besvarSporsmal(tag = "TIL_SLUTT", svar = "Jeg er ærlig!", ferdigBesvart = false)
            .besvarSporsmal(tag = "BEKREFT_OPPLYSNINGER", svar = "CHECKED")
            .sendSoknad()
        assertThat(sendtSoknad.status).isEqualTo(RSSoknadstatus.SENDT)

        sykepengesoknadKafkaConsumer.ventPåRecords(antall = 1)
        juridiskVurderingKafkaConsumer.ventPåRecords(antall = 2)
    }

    @Test
    @Order(6)
    fun `Korrigeringsfristen er ikke utløpt på den vi sendte sist`() {
        val soknaden = hentSoknader(fnr).first { it.status == RSSoknadstatus.SENDT }
        soknaden.korrigeringsfristUtlopt `should be` false
    }

    @Test
    @Order(7)
    fun `Korrigeringsfristen er utløpt på den vi sendte sist hvis den den korrigerer er 13 mnd gammel`() {
        val soknadenSomBleKorrigert = hentSoknader(fnr).first { it.status == RSSoknadstatus.KORRIGERT }

        sykepengesoknadRepository.findBySykepengesoknadUuid(soknadenSomBleKorrigert.id)?.let {
            sykepengesoknadRepository.save(it.copy(sendt = OffsetDateTime.now().minusMonths(13).toInstant()))
        }

        val hentSoknader = hentSoknader(fnr)
        val soknaden = hentSoknader.first { it.status == RSSoknadstatus.SENDT }
        soknaden.korrigeringsfristUtlopt `should be` true
    }

    @Test
    @Order(8)
    fun `Backend returnerer 400 hvis vi prøver å korrigere en søknad som har utløpt frist`() {
        val hentSoknader = hentSoknader(fnr)
        val soknaden = hentSoknader.first { it.status == RSSoknadstatus.SENDT }
        soknaden.korrigeringsfristUtlopt `should be` true

        val contentAsString = korrigerSoknadMedResult(soknaden.id, fnr).andExpect(status().isBadRequest)
            .andReturn().response.contentAsString

        contentAsString `should be equal to` "{\"reason\":\"KORRIGERINGSFRIST_UTLOPT\"}"
    }
}
