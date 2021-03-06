package no.nav.helse.flex.tokenx

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.BaseTestClass
import no.nav.helse.flex.controller.domain.RSMottakerResponse
import no.nav.helse.flex.controller.domain.RSOppdaterSporsmalResponse
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.util.OBJECT_MAPPER
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.util.*

fun BaseTestClass.jwt(fnr: String) = server.tokenxToken(fnr = fnr)

fun BaseTestClass.hentSoknader(fnr: String): List<RSSykepengesoknad> {
    val json = mockMvc.perform(
        MockMvcRequestBuilders.get("/api/v2/soknader")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

    return OBJECT_MAPPER.readValue<List<RSSykepengesoknad>>(json).sortedBy { it.opprettetDato }
}

fun BaseTestClass.lagreSvarMedResult(fnr: String, soknadId: String, sporsmalId: String, svar: RSSvar): ResultActions {
    return this.mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/sporsmal/$sporsmalId/svar")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .content(OBJECT_MAPPER.writeValueAsString(svar))
            .contentType(MediaType.APPLICATION_JSON)
    )
}

fun BaseTestClass.lagreSvar(fnr: String, soknadId: String, sporsmalId: String, svar: RSSvar): RSOppdaterSporsmalResponse {
    val json = lagreSvarMedResult(fnr, soknadId, sporsmalId, svar)
        .andExpect(MockMvcResultMatchers.status().isCreated)
        .andReturn().response.contentAsString

    return OBJECT_MAPPER.readValue(json)
}

fun BaseTestClass.slettSvarMedResult(
    fnr: String,
    soknadId: String,
    sporsmalId: String,
    svarId: String
): ResultActions {
    return this.mockMvc.perform(
        MockMvcRequestBuilders.delete("/api/v2/soknader/$soknadId/sporsmal/$sporsmalId/svar/$svarId")
            .header("Authorization", "Bearer ${jwt(fnr)}")
    )
}

fun BaseTestClass.slettSvar(fnr: String, soknadId: String, sporsmalId: String, svarId: String) {
    slettSvarMedResult(fnr, soknadId, sporsmalId, svarId).andExpect(MockMvcResultMatchers.status().isNoContent)
        .andReturn()
}

fun BaseTestClass.hentSoknaderSomVeilederObo(fnr: String, token: String): List<RSSykepengesoknad> {
    val json = mockMvc.perform(
        MockMvcRequestBuilders.get("/api/v2/veileder/soknader?fnr=$fnr")
            .header("Authorization", "Bearer $token")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

    return OBJECT_MAPPER.readValue(json)
}

fun BaseTestClass.opprettUtlandssoknad(fnr: String) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/opprettSoknadUtland")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn()
}

fun BaseTestClass.korrigerSoknadMedResult(soknadId: String, fnr: String): ResultActions {
    return mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/korriger")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    )
}

fun BaseTestClass.korrigerSoknad(soknadId: String, fnr: String): RSSykepengesoknad {
    val json = this.korrigerSoknadMedResult(soknadId, fnr).andExpect(MockMvcResultMatchers.status().isOk)
        .andReturn().response.contentAsString
    return OBJECT_MAPPER.readValue(json)
}

fun BaseTestClass.finnMottakerAvSoknad(soknadId: String, fnr: String): RSMottakerResponse {
    val json = mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/finnMottaker")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk)
        .andReturn().response.contentAsString
    return OBJECT_MAPPER.readValue(json)
}

fun BaseTestClass.gjenapneSoknad(soknadId: String, fnr: String) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/gjenapne")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString
}

fun BaseTestClass.avbrytSoknad(soknadId: String, fnr: String) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/avbryt")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString
}

fun BaseTestClass.ettersendTilNav(soknadId: String, fnr: String) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/ettersendTilNav")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString
}

fun BaseTestClass.ettersendTilArbeidsgiver(soknadId: String, fnr: String) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/ettersendTilArbeidsgiver")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString
}

fun BaseTestClass.sendSoknadMedResult(fnr: String, soknadId: String): ResultActions {
    return mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/send")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON)
    )
}

fun BaseTestClass.sendSoknad(fnr: String, soknadId: String): ResultActions {
    return this.sendSoknadMedResult(fnr, soknadId).andExpect(((MockMvcResultMatchers.status().isOk)))
}

fun BaseTestClass.oppdaterSporsmalMedResult(fnr: String, rsSporsmal: RSSporsmal, soknadsId: String): ResultActions {
    return mockMvc.perform(
        MockMvcRequestBuilders.put("/api/v2/soknader/$soknadsId/sporsmal/${rsSporsmal.id}")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .content(OBJECT_MAPPER.writeValueAsString(rsSporsmal))
            .contentType(MediaType.APPLICATION_JSON)
    )
}

fun BaseTestClass.oppdaterSporsmal(fnr: String, rsSporsmal: RSSporsmal, soknadsId: String): RSOppdaterSporsmalResponse {
    val json =
        this.oppdaterSporsmalMedResult(fnr, rsSporsmal, soknadsId).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response.contentAsString
    return OBJECT_MAPPER.readValue(json)
}

fun MockOAuth2Server.tokenxToken(
    fnr: String,
    audience: String = "sykepengesoknad-backend-client-id",
    issuerId: String = "tokenx",
    clientId: String = "sykepengesoknad-frontend-client-id",
    claims: Map<String, Any> = mapOf(
        "acr" to "Level4",
        "idp" to "idporten",
        "client_id" to clientId,
        "pid" to fnr,
    ),
): String {

    return this.issueToken(
        issuerId,
        clientId,
        DefaultOAuth2TokenCallback(
            issuerId = issuerId,
            subject = UUID.randomUUID().toString(),
            audience = listOf(audience),
            claims = claims,
            expiry = 3600
        )
    ).serialize()
}
