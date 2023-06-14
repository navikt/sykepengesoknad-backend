package no.nav.helse.flex.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknadMetadata
import no.nav.helse.flex.jwt
import no.nav.helse.flex.sendSykmelding
import no.nav.helse.flex.skapAzureJwt
import no.nav.helse.flex.sykepengesoknad.kafka.SykepengesoknadDTO
import no.nav.helse.flex.testdata.heltSykmeldt
import no.nav.helse.flex.testdata.sykmeldingKafkaMessage
import no.nav.helse.flex.util.OBJECT_MAPPER
import org.amshove.kluent.shouldBeEqualTo
import org.amshove.kluent.shouldHaveSize
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

class FlexAPITest : BaseTestClass() {
    val fnr = "12345678901"
    lateinit var kafkaMelding: SykepengesoknadDTO

    @BeforeAll
    fun `Legger til søknader i databasen`() {
        kafkaMelding = sendSykmelding(
            sykmeldingKafkaMessage(
                fnr = fnr,
                sykmeldingsperioder = heltSykmeldt()
            )
        ).first()
    }

    @Test
    fun `Kan hente søknader fra flex-internal-frontend`() {
        val result = mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/flex/sykepengesoknader")
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                    .header("fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

        val fraRest: List<RSSykepengesoknadMetadata> = OBJECT_MAPPER.readValue(result.response.contentAsString)
        fraRest.shouldHaveSize(1)
        fraRest[0].id.shouldBeEqualTo(kafkaMelding.id)
        fraRest[0].sykmeldingId.shouldBeEqualTo(kafkaMelding.sykmeldingId)
    }

    @Test
    fun `Andre apper kan ikke hente søknader`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/flex/sykepengesoknader")
                    .header("Authorization", "Bearer ${skapAzureJwt("en-annen-client-id")}")
                    .header("fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Kan ikke hente søknader som bruker`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/flex/sykepengesoknader")
                    .header("Authorization", "Bearer ${jwt(fnr)}")
                    .header("fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON)
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }
}