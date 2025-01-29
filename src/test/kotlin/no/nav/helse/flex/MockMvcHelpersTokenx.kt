package no.nav.helse.flex

import com.fasterxml.jackson.module.kotlin.readValue
import no.nav.helse.flex.controller.HentSoknaderRequest
import no.nav.helse.flex.controller.HentSoknaderResponse
import no.nav.helse.flex.controller.domain.RSMottakerResponse
import no.nav.helse.flex.controller.domain.RSOppdaterSporsmalResponse
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSporsmal
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSvar
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknad
import no.nav.helse.flex.controller.domain.sykepengesoknad.RSSykepengesoknadMetadata
import no.nav.helse.flex.util.objectMapper
import no.nav.helse.flex.util.serialisertTilString
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import org.amshove.kluent.`should be null`
import org.amshove.kluent.`should not be null`
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.util.*

fun TestOppsettInterfaces.jwt(
    fnr: String,
    acrClaim: String = "idporten-loa-high",
) = server().tokenxToken(fnr = fnr, acrClaim = acrClaim)

fun MockOAuth2Server.jwt(
    fnr: String,
    acrClaim: String = "idporten-loa-high",
) = tokenxToken(fnr = fnr, acrClaim = acrClaim)

fun FellesTestOppsett.hentSoknaderMetadataCustomAcr(
    fnr: String,
    acrClaim: String,
): String {
    val responsKode =
        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/v2/soknader/metadata")
                .header("Authorization", "Bearer ${jwt(fnr, acrClaim)}")
                .contentType(MediaType.APPLICATION_JSON),
        ).andReturn().response.status.toString()

    return responsKode
}

fun FellesTestOppsett.hentSoknaderMetadata(fnr: String): List<RSSykepengesoknadMetadata> {
    val json =
        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/v2/soknader/metadata")
                .header("Authorization", "Bearer ${jwt(fnr)}")
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun FellesTestOppsett.hentSomArbeidsgiver(req: HentSoknaderRequest): List<HentSoknaderResponse> {
    val json =
        mockMvc.perform(
            MockMvcRequestBuilders.post("/api/v1/arbeidsgiver/soknader")
                .header(
                    "Authorization",
                    "Bearer ${server.tokenxToken(fnr = "whatever", clientId = "spinntekstmelding-frontend-client-id")}",
                )
                .contentType(MediaType.APPLICATION_JSON)
                .content(req.serialisertTilString()),
        ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun FellesTestOppsett.hentSoknad(
    soknadId: String,
    fnr: String,
): RSSykepengesoknad {
    val json =
        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/v2/soknad/$soknadId")
                .header("Authorization", "Bearer ${jwt(fnr)}")
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun FellesTestOppsett.hentSoknader(fnr: String): List<RSSykepengesoknad> =
    hentSoknaderMetadata(fnr).map {
        hentSoknad(it.id, fnr)
    }

fun FellesTestOppsett.lagreSvarMedResult(
    fnr: String,
    soknadId: String,
    sporsmalId: String,
    svar: RSSvar,
): ResultActions {
    return this.mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/sporsmal/$sporsmalId/svar")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .content(objectMapper.writeValueAsString(svar))
            .contentType(MediaType.APPLICATION_JSON),
    )
}

fun FellesTestOppsett.lagreSvar(
    fnr: String,
    soknadId: String,
    sporsmalId: String,
    svar: RSSvar,
): RSOppdaterSporsmalResponse {
    val json =
        lagreSvarMedResult(fnr, soknadId, sporsmalId, svar)
            .andExpect(MockMvcResultMatchers.status().isCreated)
            .andReturn().response.contentAsString

    return objectMapper.readValue(json)
}

fun FellesTestOppsett.slettSvarMedResult(
    fnr: String,
    soknadId: String,
    sporsmalId: String,
    svarId: String,
): ResultActions {
    return this.mockMvc.perform(
        MockMvcRequestBuilders.delete("/api/v2/soknader/$soknadId/sporsmal/$sporsmalId/svar/$svarId")
            .header("Authorization", "Bearer ${jwt(fnr)}"),
    )
}

fun FellesTestOppsett.slettSvar(
    fnr: String,
    soknadId: String,
    sporsmalId: String,
    svarId: String,
) {
    slettSvarMedResult(fnr, soknadId, sporsmalId, svarId).andExpect(MockMvcResultMatchers.status().isNoContent)
        .andReturn()
}

fun FellesTestOppsett.leggTilUndersporsmal(
    fnr: String,
    soknadId: String,
    sporsmalId: String,
): ResultActions {
    return this.mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/sporsmal/$sporsmalId/undersporsmal")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON),
    )
}

fun FellesTestOppsett.slettUndersporsmal(
    fnr: String,
    soknadId: String,
    sporsmalId: String,
    undersporsmalId: String,
): ResultActions {
    return this.mockMvc.perform(
        MockMvcRequestBuilders.delete("/api/v2/soknader/$soknadId/sporsmal/$sporsmalId/undersporsmal/$undersporsmalId")
            .header("Authorization", "Bearer ${jwt(fnr)}"),
    )
}

fun FellesTestOppsett.opprettUtlandssoknad(fnr: String) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/opprettSoknadUtland")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON),
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn()
}

fun FellesTestOppsett.korrigerSoknadMedResult(
    soknadId: String,
    fnr: String,
): ResultActions {
    return mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/korriger")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON),
    )
}

fun FellesTestOppsett.korrigerSoknad(
    soknadId: String,
    fnr: String,
): RSSykepengesoknad {
    val json =
        this.korrigerSoknadMedResult(soknadId, fnr).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response.contentAsString
    return objectMapper.readValue(json)
}

fun FellesTestOppsett.finnMottakerAvSoknad(
    soknadId: String,
    fnr: String,
): RSMottakerResponse {
    val json =
        mockMvc.perform(
            MockMvcRequestBuilders.get("/api/v2/soknader/$soknadId/mottaker")
                .header("Authorization", "Bearer ${jwt(fnr)}")
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response.contentAsString
    return objectMapper.readValue(json)
}

fun FellesTestOppsett.gjenapneSoknad(
    soknadId: String,
    fnr: String,
) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/gjenapne")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON),
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString
}

fun FellesTestOppsett.avbrytSoknad(
    soknadId: String,
    fnr: String,
) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/avbryt")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON),
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString
}

fun FellesTestOppsett.ettersendTilNav(
    soknadId: String,
    fnr: String,
) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/ettersendTilNav")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON),
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString
}

fun FellesTestOppsett.ettersendTilArbeidsgiver(
    soknadId: String,
    fnr: String,
) {
    mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/ettersendTilArbeidsgiver")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON),
    ).andExpect(MockMvcResultMatchers.status().isOk).andReturn().response.contentAsString
}

fun FellesTestOppsett.sendSoknadMedResult(
    fnr: String,
    soknadId: String,
): ResultActions {
    return mockMvc.perform(
        MockMvcRequestBuilders.post("/api/v2/soknader/$soknadId/send")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .contentType(MediaType.APPLICATION_JSON),
    )
}

fun FellesTestOppsett.sendSoknad(
    fnr: String,
    soknadId: String,
): ResultActions {
    return this.sendSoknadMedResult(fnr, soknadId).andExpect(((MockMvcResultMatchers.status().isOk)))
}

fun FellesTestOppsett.oppdaterSporsmalMedResult(
    fnr: String,
    rsSporsmal: RSSporsmal,
    soknadsId: String,
): ResultActions {
    return mockMvc.perform(
        MockMvcRequestBuilders.put("/api/v2/soknader/$soknadsId/sporsmal/${rsSporsmal.id}")
            .header("Authorization", "Bearer ${jwt(fnr)}")
            .content(objectMapper.writeValueAsString(rsSporsmal))
            .contentType(MediaType.APPLICATION_JSON),
    )
}

fun FellesTestOppsett.oppdaterSporsmal(
    fnr: String,
    rsSporsmal: RSSporsmal,
    soknadsId: String,
    mutert: Boolean,
): RSOppdaterSporsmalResponse {
    val json =
        this.oppdaterSporsmalMedResult(fnr, rsSporsmal, soknadsId).andExpect(MockMvcResultMatchers.status().isOk)
            .andReturn().response.contentAsString
    val response = objectMapper.readValue<RSOppdaterSporsmalResponse>(json)
    if (mutert) {
        response.mutertSoknad.`should not be null`()
    } else {
        response.mutertSoknad.`should be null`()
    }
    return response
}

fun MockOAuth2Server.tokenxToken(
    fnr: String,
    acrClaim: String = "idporten-loa-high",
    audience: String = "sykepengesoknad-backend-client-id",
    issuerId: String = "tokenx",
    clientId: String = "sykepengesoknad-frontend-client-id",
    claims: Map<String, Any> =
        mapOf(
            "acr" to acrClaim,
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
            expiry = 3600,
        ),
    ).serialize()
}
