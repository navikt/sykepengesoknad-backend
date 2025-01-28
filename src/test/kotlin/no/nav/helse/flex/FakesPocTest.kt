package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknadMetadata
import no.nav.helse.flex.util.objectMapper
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

class FakesPocTest : FakesTestOppsett() {
    fun hentSoknaderMetadata(fnr: String): List<RSSykepengesoknadMetadata> {
        val json =
            mockMvc.perform(
                MockMvcRequestBuilders.get("/api/v2/soknader/metadata")
                    .header("Authorization", "Bearer ${jwt(fnr)}")
                    .contentType(MediaType.APPLICATION_JSON),
            ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

        return objectMapper.readValue(json)
    }

    @Test
    fun `context laster`() {
    }
}
