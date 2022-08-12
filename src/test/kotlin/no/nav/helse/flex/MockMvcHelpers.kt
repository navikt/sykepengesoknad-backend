package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.util.*

fun BaseTestClass.buildAzureClaimSet(
    subject: String,
    issuer: String = "azureator",
    audience: String = "syfosoknad-client-id"
): String {

    val claims = HashMap<String, String>()

    return server.token(
        subject = "Vi sjekker azp",
        issuerId = issuer,
        clientId = subject,
        audience = audience,
        claims = claims
    )
}

fun BaseTestClass.skapAzureJwt(subject: String = "sykepengesoknad-korrigering-metrikk-client-id") =
    buildAzureClaimSet(subject = subject)

fun BaseTestClass.hentSoknaderSomVeilederObo(fnr: String, token: String): List<RSSykepengesoknad> {
    val json = mockMvc.perform(
        MockMvcRequestBuilders.get("/api/veileder/soknader?fnr=$fnr")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

    return OBJECT_MAPPER.readValue(json)
}

fun MockOAuth2Server.token(
    subject: String,
    issuerId: String = "selvbetjening",
    clientId: String = UUID.randomUUID().toString(),
    audience: String = "loginservice-client-id",
    claims: Map<String, Any> = mapOf("acr" to "Level4"),

): String {
    return this.issueToken(
        issuerId,
        clientId,
        DefaultOAuth2TokenCallback(
            issuerId = issuerId,
            subject = subject,
            audience = listOf(audience),
            claims = claims,
            expiry = 3600
        )
    ).serialize()
}
