package no.nav.helse.flex.controller

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.FellesTestOppsett
import no.nav.helse.flex.client.pdl.PdlIdent
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

class FlexAPITest : FellesTestOppsett() {
    val fnr = "12345678901"
    lateinit var kafkaMelding: SykepengesoknadDTO

    @BeforeAll
    fun `Legger til søknader i databasen`() {
        kafkaMelding =
            sendSykmelding(
                sykmeldingKafkaMessage(
                    fnr = fnr,
                    sykmeldingsperioder = heltSykmeldt(),
                ),
            ).first()
    }

    @Test
    fun `Kan hente søknader og enkelt søknad fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v1/flex/sykepengesoknader")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("fnr", fnr)
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

        val fraRest: FlexInternalResponse = OBJECT_MAPPER.readValue(result.response.contentAsString)
        fraRest.sykepengesoknadListe.shouldHaveSize(1)
        fraRest.klippetSykepengesoknadRecord.shouldHaveSize(0)
        fraRest.sykepengesoknadListe[0].id.shouldBeEqualTo(kafkaMelding.id)
        fraRest.sykepengesoknadListe[0].sykmeldingId.shouldBeEqualTo(kafkaMelding.sykmeldingId)

        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/flex/sykepengesoknader/" + kafkaMelding.id)
                    .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                    .contentType(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().isOk).andReturn().let {
                val res: FlexInternalSoknadResponse = OBJECT_MAPPER.readValue(it.response.contentAsString)
                res.sykepengesoknad.id.shouldBeEqualTo(kafkaMelding.id)
            }
    }

    @Test
    fun `Kan hente enkelt søknad fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v1/flex/sykepengesoknader")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("fnr", fnr)
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

        val fraRest: FlexInternalResponse = OBJECT_MAPPER.readValue(result.response.contentAsString)
        fraRest.sykepengesoknadListe.shouldHaveSize(1)
        fraRest.klippetSykepengesoknadRecord.shouldHaveSize(0)
        fraRest.sykepengesoknadListe[0].id.shouldBeEqualTo(kafkaMelding.id)
        fraRest.sykepengesoknadListe[0].sykmeldingId.shouldBeEqualTo(kafkaMelding.sykmeldingId)
    }

    @Test
    fun `Andre apper kan ikke hente søknader`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/flex/sykepengesoknader")
                    .header("Authorization", "Bearer ${skapAzureJwt("en-annen-client-id")}")
                    .header("fnr", fnr)
                    .contentType(MediaType.APPLICATION_JSON),
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
                    .contentType(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }

    @Test
    fun `Kan hente identer fra flex-internal-frontend`() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/api/v1/flex/identer")
                        .header("Authorization", "Bearer ${skapAzureJwt("flex-internal-frontend-client-id")}")
                        .header("ident", "211111111111")
                        .contentType(MediaType.APPLICATION_JSON),
                )
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn()

        val fraRest: List<PdlIdent> = OBJECT_MAPPER.readValue(result.response.contentAsString)
        fraRest.shouldHaveSize(3)
        fraRest[0] shouldBeEqualTo PdlIdent("FOLKEREGISTERIDENT", "211111111111")
        fraRest[1] shouldBeEqualTo PdlIdent("FOLKEREGISTERIDENT", "111111111111")
        fraRest[2] shouldBeEqualTo PdlIdent("AKTORID", "21111111111100")
    }

    @Test
    fun `Kan ikke hente identer som bruker`() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("/api/v1/flex/identer")
                    .header("Authorization", "Bearer ${jwt(fnr)}")
                    .header("ident", "211111111111")
                    .contentType(MediaType.APPLICATION_JSON),
            )
            .andExpect(MockMvcResultMatchers.status().is4xxClientError)
    }
}
