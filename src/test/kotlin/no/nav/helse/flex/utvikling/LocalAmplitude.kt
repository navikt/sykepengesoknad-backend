package no.nav.helse.flex.utvikling

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.flex.util.objectMapper
import no.nav.security.token.support.core.api.Unprotected
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.*

@RestController
@Profile("dev")
class LocalAmplitude {
    val requests = mutableListOf<JsonNode>()

    @PostMapping(value = ["/api/amplitude"], consumes = [MediaType.APPLICATION_JSON_VALUE])
    @CrossOrigin
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Unprotected
    fun amplitudePost(
        @RequestBody request: String,
    ) {
        val rootNode = objectMapper.readTree(request)

        requests.add(rootNode)
    }

    @GetMapping(value = ["/api/amplitude"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @CrossOrigin
    @Unprotected
    fun amplitudeGet(): Response =
        Response(
            requests,
            AmplitudeUserdata(
                userId = "123",
                firstUsed = "2021-01-01",
                lastUsed = "2021-01-01",
                device = "device",
                os = "os",
                properties =
                    Properties(
                        vindusHoyde = 100,
                        vindusBredde = 100,
                    ),
            ),
        )

    data class AmplitudeUserdata(
        @JsonProperty("user_id") val userId: String,
        @JsonProperty("first_used") val firstUsed: String,
        @JsonProperty("last_used") val lastUsed: String,
        val device: String,
        val os: String,
        val properties: Properties,
    )

    data class Properties(
        @JsonProperty("vindushoyde") val vindusHoyde: Int,
        @JsonProperty("vindusbredde") val vindusBredde: Int,
    )

    data class Response(
        val events: List<JsonNode>,
        val userData: AmplitudeUserdata,
    )
}
