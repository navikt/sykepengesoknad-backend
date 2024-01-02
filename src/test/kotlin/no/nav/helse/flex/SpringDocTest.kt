package no.nav.helse.flex

import org.amshove.kluent.shouldContain
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

class SpringDocTest : BaseTestClass() {
    @Test
    fun `har springdoc`() {
        val response =
            mockMvc
                .perform(MockMvcRequestBuilders.get("/v3/api-docs"))
                .andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

        response.shouldContain("OpenAPI definition")
        response.shouldContain("oppdaterSporsmal")
    }
}
