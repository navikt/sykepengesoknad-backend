package no.nav.helse.flex.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.controller.SoknadKafkaFormatController.HentSoknaderRequest
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.skapAzureJwt
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import no.nav.syfo.kafka.NAV_CALLID
import org.amshove.kluent.shouldBeEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.time.LocalDate
import java.time.temporal.ChronoUnit.SECONDS
import java.util.*

class SoknadKafkaFormatControllerTest : FellesTestOppsett() {
    @Test
    fun `Vi kan hente en søknad på samme format som kafka topicet med version 2 token`() {
        val kafkaSoknad =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = "1234",
                    sykmeldingsperioder = heltSykmeldt(),
                ),
            ).first()

        val fraRest =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v3/soknader/${kafkaSoknad.id}/kafkaformat")
                        .header("Authorization", "Bearer ${skapAzureJwt()}")
                        .header(NAV_CALLID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .let { objectMapper.readValue<SykepengesoknadDTO>(it.response.contentAsString) }

        val listeFraRest =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/api/v3/soknader")
                        .content(
                            HentSoknaderRequest(
                                fnr = kafkaSoknad.fnr,
                                fom = LocalDate.of(2020, 2, 1),
                                tom = LocalDate.of(2020, 2, 15),
                                medSporsmal = true,
                            ).serialisertTilString(),
                        ).header("Authorization", "Bearer ${skapAzureJwt(subject = "bakrommet-client-id")}")
                        .header(NAV_CALLID, UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
                .let { objectMapper.readValue<List<SykepengesoknadDTO>>(it.response.contentAsString) }

        assertThat(fraRest.fnr).isEqualTo(kafkaSoknad.fnr)

        fun SykepengesoknadDTO.fjernMs(): SykepengesoknadDTO =
            this.copy(
                opprettet = opprettet?.truncatedTo(SECONDS),
                sykmeldingSkrevet = sykmeldingSkrevet?.truncatedTo(SECONDS),
            )

        fraRest.fjernMs().shouldBeEqualTo(kafkaSoknad.fjernMs())
        fraRest.fjernMs().shouldBeEqualTo(listeFraRest[0].fjernMs())
    }

    @Test
    fun `Vi får 401 om vi ikke sender auth header`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/whatever/kafkaformat")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andReturn()
    }

    @Test
    fun `Vi får 401 om vi har jibberish i auth headeren`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/whatever/kafkaformat")
                    .header("Authorization", "Bearer sdafsdaf")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andReturn()

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/whatever/kafkaformat")
                    .header("Authorization", "dsgfdgdfgf")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isUnauthorized)
            .andReturn()
    }

    @Test
    fun `Vi får 403 om vi har feil subject`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/whatever/kafkaformat")
                    .header("Authorization", "Bearer ${skapAzureJwt("facebook")}")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isForbidden)
            .andReturn()
    }

    @Test
    fun `Vi får 404 om vi ikke finner søknaden`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v3/soknader/e65e14ad-35eb-4695-add9-9f26ba120818/kafkaformat")
                    .header("Authorization", "Bearer ${skapAzureJwt()}")
                    .header(NAV_CALLID, UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isNotFound)
            .andReturn()
    }
}
