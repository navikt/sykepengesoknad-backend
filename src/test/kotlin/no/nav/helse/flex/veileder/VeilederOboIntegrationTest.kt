package no.nav.helse.flex.veileder

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.SoknadVeilederAzureController
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.mockIstilgangskontroll
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.skapAzureJwt
import no.nav.helse.flex.soknadsopprettelse.*
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@TestMethodOrder(MethodOrderer.MethodName::class)
class VeilederOboIntegrationTest : FellesTestOppsett() {
    final val fnr = "123456789"

    @BeforeAll
    fun konfigurerUnleash() {
        fakeUnleash.resetAll()
    }

    @Test
    fun `01 - vi oppretter en arbeidsledigsøknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG,
            ),
        )
    }

    @Test
    fun `02 - vi kan hente søknaden som veileder`() {
        val veilederToken = skapAzureJwt("syfomodiaperson-client-id")
        mockIstilgangskontroll(true, fnr)

        val soknader = hentSoknaderSomVeileder(fnr, veilederToken)
        assertThat(soknader).hasSize(1)

        val soknaden = soknader.first()
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                OPPHOLD_UTENFOR_EOS,
                TIL_SLUTT,
            ),
        )
        istilgangskontrollMockRestServiceServer.verify()
        istilgangskontrollMockRestServiceServer.reset()
    }

    @Test
    fun `03 - vi kan ikke hente søknaden som veileder uten tilgang`() {
        val veilederToken = skapAzureJwt("syfomodiaperson-client-id")
        mockIstilgangskontroll(false, fnr)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/veileder/soknader")
                    .content(
                        SoknadVeilederAzureController.HentVeilederSoknaderRequest(fnr).serialisertTilString(),
                    ).header("Authorization", "Bearer $veilederToken")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
            .andReturn()
            .response.contentAsString
        istilgangskontrollMockRestServiceServer.verify()
        istilgangskontrollMockRestServiceServer.reset()
    }

    @Test
    fun `04 - api krever body`() {
        val veilederToken = skapAzureJwt("syfomodiaperson-client-id")

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/api/veileder/soknader")
                    .header("Authorization", "Bearer $veilederToken")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isInternalServerError)
            .andReturn()
            .response.contentAsString
    }

    fun FellesTestOppsett.hentSoknaderSomVeileder(
        fnr: String,
        token: String,
    ): List<RSSykepengesoknad> {
        val json =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/veileder/soknader")
                        .header("Authorization", "Bearer $token")
                        .content(
                            SoknadVeilederAzureController.HentVeilederSoknaderRequest(fnr).serialisertTilString(),
                        ).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .response.contentAsString

        return objectMapper.readValue(json)
    }
}
