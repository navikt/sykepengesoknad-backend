package no.nav.helse.flex.util

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

// Jackson 3 leser og skriver enums med toString() som standard. Vi bruker konstantnavnet, ellers
// kolliderer enums som deler tekst i toString() (f.eks. Arbeidssituasjon.FISKER og .JORDBRUKER).
// READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE var også på før Jackson 3 og beholdes.
fun JsonMapper.Builder.medFlexEnumOppsett(): JsonMapper.Builder =
    this
        .disable(EnumFeature.READ_ENUMS_USING_TO_STRING)
        .disable(EnumFeature.WRITE_ENUMS_USING_TO_STRING)
        .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE)

val objectMapper: ObjectMapper =
    JsonMapper
        .builder()
        .addModule(kotlinModule())
        .medFlexEnumOppsett()
        .build()

fun Any.serialisertTilString(): String = objectMapper.writeValueAsString(this)

fun Any.toJsonNode(): JsonNode = objectMapper.readTree(objectMapper.writeValueAsString(this))
