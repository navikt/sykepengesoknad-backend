package no.nav.helse.flex.veileder

import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.domain.Arbeidssituasjon
import no.nav.helse.flex.hentSoknaderSomVeilederObo
import no.nav.helse.flex.mockSyfoTilgangskontroll
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.skapAzureJwt
import no.nav.helse.flex.soknadsopprettelse.ANDRE_INNTEKTSKILDER
import no.nav.helse.flex.soknadsopprettelse.ANSVARSERKLARING
import no.nav.helse.flex.soknadsopprettelse.ARBEIDSLEDIG_UTLAND
import no.nav.helse.flex.soknadsopprettelse.ARBEID_UTENFOR_NORGE
import no.nav.helse.flex.soknadsopprettelse.BEKREFT_OPPLYSNINGER
import no.nav.helse.flex.soknadsopprettelse.FRISKMELDT
import no.nav.helse.flex.soknadsopprettelse.VAER_KLAR_OVER_AT
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.MethodOrderer
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@TestMethodOrder(MethodOrderer.MethodName::class)
class VeilederOboIntegrationTest : BaseTestClass() {

    final val fnr = "123456789"

    @Test
    fun `01 - vi oppretter en arbeidsledigsøknad`() {
        sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                arbeidssituasjon = Arbeidssituasjon.ARBEIDSLEDIG
            )
        )
    }

    @Test
    fun `02 - vi kan hente søknaden som veileder`() {
        val veilederToken = skapAzureJwt("syfomodiaperson-client-id")
        mockSyfoTilgangskontroll(true, fnr)

        val soknader = hentSoknaderSomVeilederObo(fnr, veilederToken)
        assertThat(soknader).hasSize(1)
        val soknaden = soknader.first()
        assertThat(soknaden.sporsmal!!.map { it.tag }).isEqualTo(
            listOf(
                ANSVARSERKLARING,
                FRISKMELDT,
                ARBEID_UTENFOR_NORGE,
                ANDRE_INNTEKTSKILDER,
                ARBEIDSLEDIG_UTLAND,
                VAER_KLAR_OVER_AT,
                BEKREFT_OPPLYSNINGER
            )
        )
        syfotilgangskontrollMockRestServiceServer.verify()
        syfotilgangskontrollMockRestServiceServer.reset()
    }

    @Test
    fun `03 - vi kan ikke hente søknaden som veileder uten tilgang`() {
        val veilederToken = skapAzureJwt("syfomodiaperson-client-id")
        mockSyfoTilgangskontroll(false, fnr)

        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/veileder/soknader?fnr=$fnr")
                .header("Authorization", "Bearer $veilederToken")
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError).andReturn().response.contentAsString
        syfotilgangskontrollMockRestServiceServer.verify()
        syfotilgangskontrollMockRestServiceServer.reset()
    }
}
